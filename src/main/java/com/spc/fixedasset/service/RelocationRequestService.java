package com.spc.fixedasset.service;

import com.spc.fixedasset.auth.HrRepository;
import com.spc.fixedasset.dto.*;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.ConflictException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.model.HistoryTableInfo;
import com.spc.fixedasset.model.LocationMapRow;
import com.spc.fixedasset.model.RelocationAsset;
import com.spc.fixedasset.model.RelocationHistoryRow;
import com.spc.fixedasset.repository.LocationRepository;
import com.spc.fixedasset.repository.RelocationRequestRepository;
import com.spc.fixedasset.repository.RelocationRequestRepository.Filter;
import com.spc.fixedasset.service.LocationMatcher.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.spc.fixedasset.config.DrawingStorageConfig;
import com.spc.fixedasset.storage.DrawingStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.spc.fixedasset.service.LocationMatcher.*;

/** Relocation requests: one F2_FIXED_ASSET_HISTORY row per machine, grouped by RequestNo. Never writes F2_FIXED_ASSET. */
@Service
public class RelocationRequestService {

    /** Flow: REQ_PENDING -> REQ_APPROVED / REQ_REJECTED -> REQ_DONE (no PE step; ApproverPE stays null). */
    public static final String PENDING = "REQ_PENDING";
    public static final String APPROVED = "REQ_APPROVED";
    public static final List<String> STATUSES = List.of(PENDING, APPROVED, "REQ_REJECTED", "REQ_DONE");
    /** A machine in one of these cannot get another request (also enforced by UX_F2FAH_OpenRequest). */
    public static final List<String> OPEN_STATUSES = List.of(PENDING, APPROVED);
    /** Same values as the FE config/relocation.ts; compared trim/case-insensitively with F2_FIXED_ASSET.KindFixedAsset. */
    public static final List<String> ALLOWED_KINDS = List.of("Machinery", "Tools", "Furniture and Fixtures");
    public static final String OUTSIDE_FAC = "Outside";
    static final int MAX_MACHINES = 500;
    static final int MAX_PAGE_SIZE = 100;

    private final RelocationRequestRepository repository;
    private final LocationRepository locations;
    private final Clock clock;
    private final HrRepository hr;
    /** Excel folder; only used to build excelUrl. */
    private final DrawingStorage excelStorage;
    private static final Logger log = LoggerFactory.getLogger(RelocationRequestService.class);

    @Autowired
    public RelocationRequestService(RelocationRequestRepository repository, LocationRepository locations, HrRepository hr,
                                    @Qualifier(DrawingStorageConfig.EXCEL) DrawingStorage excelStorage) {
        this(repository, locations, hr, excelStorage, Clock.systemDefaultZone());
    }

    RelocationRequestService(RelocationRequestRepository repository, LocationRepository locations, HrRepository hr,
                             DrawingStorage excelStorage, Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.hr = hr;
        this.excelStorage = excelStorage;
        this.clock = clock;
    }

    /** account/name: the logged-in session user, never values from the body; stored as Creater "{account}_{name}". */
    @Transactional
    public RelocationCreateResponse create(RelocationCreateRequest req, String account, String name) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<String> codes = validate(req, now.toLocalDate());
        if (clean(account) == null) throw new BadRequestException("Thiếu tài khoản người tạo.");
        String reason = req.reason().trim();

        String toA = normalize(req.to().positionA()), toAA = normalizeSub(req.to().positionA(), req.to().positionAA());
        List<LocationMapRow> rows = locations.findMapRows();
        LocationMapRow dest = destination(toA, toAA, rows);
        String toZone = toAA != null ? toAA : toA;

        Map<String, RelocationAsset> assets = repository.findAssets(codes).stream()
                .collect(Collectors.toMap(RelocationAsset::code, Function.identity(), (x, y) -> x));
        List<String> notFound = codes.stream().filter(c -> !assets.containsKey(c)).toList();
        if (!notFound.isEmpty()) throw new BadRequestException("Không tìm thấy máy: " + String.join(", ", notFound));
        List<String> wrongKind = codes.stream().filter(c -> !allowedKind(assets.get(c).kind())).toList();
        if (!wrongKind.isEmpty()) throw new BadRequestException("Loại tài sản không được di dời: " + String.join(", ", wrongKind));

        List<String> open = repository.findOpenMachineCodes(codes, OPEN_STATUSES);
        if (!open.isEmpty()) throw openConflict(open);

        List<RelocationAsset> moving = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, Match> matches = new HashMap<>();
        for (String code : codes) {
            RelocationAsset a = assets.get(code);
            Match m = match(a.positionA(), a.positionAA(), a.div(), a.floor(), rows);
            matches.put(code, m);
            if (toZone.equals(currentZone(a.positionA(), a.positionAA(), m))) skipped.add(code);
            else moving.add(a);
        }
        if (moving.isEmpty()) throw new BadRequestException("Tất cả máy đã ở vị trí đích " + toZone + ".");

        HistoryTableInfo table = repository.tableInfo();
        String creater = CreaterFormat.format(account, name, table.maxLengths().get("creater"));
        String requestNo = requestNo(repository.maxRequestSeq());
        long nextId = table.idIdentity() ? 0 : repository.maxId() + 1;
        List<RelocationHistoryRow> history = new ArrayList<>();
        for (RelocationAsset a : moving) {
            String group = clean(a.group()), pic = clean(a.pic());
            history.add(new RelocationHistoryRow(table.idIdentity() ? null : nextId++, requestNo, a.code(),
                    normalize(a.positionA()), normalizeSub(a.positionA(), a.positionAA()), normalize(a.positionAAA()), group, pic,
                    toA, toAA, null, group, pic,
                    req.plannedMoveDate(), req.plannedDoneDate(), now, creater, reason, PENDING, null));
        }
        history.forEach(r -> checkLengths(r, table));
        try {
            repository.insertAll(history, !table.idIdentity());
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) throw openConflict(List.of());
            throw e;
        }

        List<RelocationCreateResponse.Item> items = history.stream()
                .map(r -> new RelocationCreateResponse.Item(r.machineCode(),
                        new RelocationPosition(r.positionABf(), r.positionAABf(), r.positionAAABf()),
                        new RelocationPosition(toA, toAA, null),
                        moveType(matches.get(r.machineCode()).row(), dest)))
                .toList();
        return new RelocationCreateResponse(requestNo, PENDING, items, skipped);
    }

    @Transactional(readOnly = true)
    public RelocationRequestPage list(String status, String machineCode, String requestedBy, Integer page, Integer size) {
        String st = clean(status);
        if (st != null && !STATUSES.contains(st)) throw new BadRequestException("status phải là một trong " + String.join(", ", STATUSES) + ".");
        int p = page == null ? 0 : page, s = size == null ? 20 : size;
        if (p < 0 || s < 1 || s > MAX_PAGE_SIZE) throw new BadRequestException("page ≥ 0, 1 ≤ size ≤ " + MAX_PAGE_SIZE + ".");
        Filter f = new Filter(st, clean(machineCode), CreaterFormat.accountOf(requestedBy));
        long total = repository.countRequests(f);
        List<String> nos = total == 0 ? List.of() : repository.findRequestNos(f, p * s, s);
        Map<String, List<RelocationHistoryRow>> byNo = repository.findRows(nos).stream()
                .collect(Collectors.groupingBy(RelocationHistoryRow::requestNo));
        Map<String, String> names = requesterNames(byNo.values().stream().map(r -> r.get(0).creater()).toList());
        List<RelocationRequestResponse> items = nos.stream().filter(byNo::containsKey).map(no -> toResponse(no, byNo.get(no), names)).toList();
        return new RelocationRequestPage(items, p, s, total);
    }

    @Transactional(readOnly = true)
    public RelocationRequestResponse get(String requestNo) {
        List<RelocationHistoryRow> rows = repository.findRows(List.of(requestNo));
        if (rows.isEmpty()) throw new NotFoundException("Không tìm thấy yêu cầu di dời: " + requestNo);
        return toResponse(requestNo, rows, requesterNames(Collections.singletonList(rows.get(0).creater())));
    }

    /** Account of the creator (accountOf Creater, old and new format); 404 when the request does not exist. */
    @Transactional(readOnly = true)
    public String creatorOf(String requestNo) {
        List<RelocationHistoryRow> rows = repository.findRows(List.of(requestNo));
        if (rows.isEmpty()) throw new NotFoundException("Không tìm thấy yêu cầu di dời: " + requestNo);
        return CreaterFormat.accountOf(rows.get(0).creater());
    }

    /** Body checks that need no database; returns the trimmed, de-duplicated machine codes (order kept). */
    static List<String> validate(RelocationCreateRequest req, LocalDate today) {
        if (req == null) throw new BadRequestException("Thiếu body.");
        if (req.machineCodes() == null || req.machineCodes().isEmpty()) throw new BadRequestException("machineCodes là bắt buộc.");
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String c : req.machineCodes()) {
            String v = clean(c);
            if (v == null) throw new BadRequestException("machineCodes có mã rỗng.");
            codes.add(v);
        }
        if (codes.size() > MAX_MACHINES) throw new BadRequestException("Tối đa " + MAX_MACHINES + " máy mỗi yêu cầu.");
        if (req.to() == null || normalize(req.to().positionA()) == null) throw new BadRequestException("to.positionA là bắt buộc.");
        if (clean(req.reason()) == null) throw new BadRequestException("reason là bắt buộc.");
        if (req.plannedMoveDate() == null || req.plannedDoneDate() == null) throw new BadRequestException("plannedMoveDate và plannedDoneDate là bắt buộc.");
        if (req.plannedMoveDate().isBefore(today)) throw new BadRequestException("plannedMoveDate không được trước hôm nay.");
        if (req.plannedDoneDate().isBefore(req.plannedMoveDate())) throw new BadRequestException("plannedDoneDate không được trước plannedMoveDate.");
        return List.copyOf(codes);
    }

    /** Destination MAP row (exact A/AA, not Outside, lowest Id), or null when there is none (never throws). */
    static LocationMapRow destinationRow(String a, String aa, List<LocationMapRow> rows) {
        String na = normalize(a);
        if (na == null) return null;
        String naa = normalizeSub(a, aa);
        return rows.stream()
                .filter(r -> na.equals(normalize(r.a())) && Objects.equals(naa, normalizeSub(r.a(), r.aa())) && !sameText(r.fac(), OUTSIDE_FAC))
                .min(Comparator.comparingLong(LocationMapRow::id)).orElse(null);
    }

    /** MAP row of the destination (exact A/AA after normalization), preferring the lowest Id; Outside is never one. */
    static LocationMapRow destination(String a, String aa, List<LocationMapRow> rows) {
        List<LocationMapRow> same = rows.stream()
                .filter(r -> a.equals(normalize(r.a())) && Objects.equals(aa, normalizeSub(r.a(), r.aa())))
                .toList();
        String zone = aa != null ? aa : a;
        if (same.isEmpty()) throw new BadRequestException("Vị trí đích không có trong F2_FIXED_ASSET_MAP: " + zone);
        return same.stream().filter(r -> !sameText(r.fac(), OUTSIDE_FAC)).min(Comparator.comparingLong(LocationMapRow::id))
                .orElseThrow(() -> new BadRequestException("Không thể di dời đến vị trí Outside: " + zone));
    }

    /** "R" + global sequence padded to 4 digits (R0001…R9999, then R10000 uncut). */
    static String requestNo(Integer maxSeq) {
        int next = (maxSeq == null ? 0 : maxSeq) + 1;
        return "R%04d".formatted(next);
    }

    /** building/floor when both MAP rows know the value and it differs; same otherwise (incl. unmatched source). */
    static String moveType(LocationMapRow from, LocationMapRow to) {
        if (from == null) return "same";
        if (clean(from.fac()) != null && clean(to.fac()) != null && !sameText(from.fac(), to.fac())) return "building";
        if (clean(from.floor()) != null && clean(to.floor()) != null && !sameText(from.floor(), to.floor())) return "floor";
        return "same";
    }

    /** SQL Server 2601 (unique index) / 2627 (unique constraint) anywhere in the cause chain. */
    static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && (s.getErrorCode() == 2601 || s.getErrorCode() == 2627)) return true;
        }
        return false;
    }

    private static ConflictException openConflict(List<String> codes) {
        return new ConflictException(codes.isEmpty()
                ? "Có máy đang có yêu cầu di dời chưa xử lý."
                : "Máy đang có yêu cầu di dời chưa xử lý: " + String.join(", ", codes), codes);
    }

    private static boolean allowedKind(String kind) {
        return ALLOWED_KINDS.stream().anyMatch(k -> sameText(k, kind));
    }

    private static void checkLengths(RelocationHistoryRow r, HistoryTableInfo t) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("RequestNo", r.requestNo());
        values.put("MachineCode", r.machineCode());
        values.put("PositionA_BF", r.positionABf());
        values.put("PositionAA_BF", r.positionAABf());
        values.put("PositionAAA_BF", r.positionAAABf());
        values.put("Group_BF", r.groupBf());
        values.put("PIC_BF", r.picBf());
        values.put("PositionA_AT", r.positionAAt());
        values.put("PositionAA_AT", r.positionAAAt());
        values.put("Group_AT", r.groupAt());
        values.put("PIC_AT", r.picAt());
        values.put("Creater", r.creater());
        values.put("Note", r.note());
        values.put("Status", r.status());
        values.forEach((col, v) -> {
            Integer max = t.maxLengths().get(col.toLowerCase(Locale.ROOT));
            if (v != null && max != null && max >= 0 && v.length() > max) {
                throw new BadRequestException("%s vượt quá %d ký tự (máy %s).".formatted(col, max, r.machineCode()));
            }
        });
    }

    /** HR names of the old (account-only) Creaters in one IN query; an HR failure only leaves those names null. */
    private Map<String, String> requesterNames(Collection<String> creaters) {
        List<String> legacy = CreaterFormat.legacyAccounts(creaters);
        if (legacy.isEmpty()) return Map.of();
        try {
            return hr.findNames(legacy);
        } catch (DataAccessException e) {
            log.warn("Không tra được tên người yêu cầu trong F2_HR_Data ({})", e.getClass().getSimpleName());
            return Map.of();
        }
    }

    private RelocationRequestResponse toResponse(String requestNo, List<RelocationHistoryRow> rows, Map<String, String> names) {
        RelocationHistoryRow first = rows.get(0);
        Set<String> statuses = rows.stream().map(RelocationHistoryRow::status).collect(Collectors.toSet());
        List<RelocationRequestResponse.Item> items = rows.stream()
                .map(r -> new RelocationRequestResponse.Item(r.machineCode(),
                        new RelocationPosition(r.positionABf(), r.positionAABf(), r.positionAAABf()),
                        new RelocationPosition(r.positionAAt(), r.positionAAAt(), r.positionAAAAt()), r.groupBf(), r.picBf(), r.status()))
                .toList();
        return new RelocationRequestResponse(requestNo, statuses.size() == 1 ? first.status() : null, first.creater(),
                CreaterFormat.accountOf(first.creater()), CreaterFormat.requesterName(first.creater(), names), first.note(),
                first.plannedMoveDate(), first.plannedDoneDate(), first.createDate(),
                new RelocationPosition(first.positionAAt(), first.positionAAAt(), first.positionAAAAt()), drawingUrl(rows),
                excelStorage.urlOrName(RelocationExcelService.fileName(requestNo, first.createDate())), items);
    }

    /** Drawings as stored: webUrl, or the file name when there is no (fitting) URL. */
    private static String drawingUrl(List<RelocationHistoryRow> rows) {
        return rows.stream().map(RelocationHistoryRow::drawings).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static String clean(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
