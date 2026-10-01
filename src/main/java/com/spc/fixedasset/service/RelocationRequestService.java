package com.spc.fixedasset.service;

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
import org.springframework.beans.factory.annotation.Autowired;
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

    public static final String STATUS_PREFIX = "REQ_";
    public static final String PENDING_PE = "REQ_PENDING_PE";
    /** A machine in one of these cannot get another request (also enforced by UX_F2FAH_OpenRequest). */
    public static final List<String> OPEN_STATUSES = List.of(PENDING_PE, "REQ_PENDING_BOD", "REQ_APPROVED");
    /** Same values as the FE config/relocation.ts; compared trim/case-insensitively with F2_FIXED_ASSET.KindFixedAsset. */
    public static final List<String> ALLOWED_KINDS = List.of("Machinery", "Tools", "Furniture and Fixtures");
    public static final String OUTSIDE_FAC = "Outside";
    static final int MAX_MACHINES = 500;
    static final int MAX_PAGE_SIZE = 100;

    private final RelocationRequestRepository repository;
    private final LocationRepository locations;
    private final Clock clock;

    @Autowired
    public RelocationRequestService(RelocationRequestRepository repository, LocationRepository locations) {
        this(repository, locations, Clock.systemDefaultZone());
    }

    RelocationRequestService(RelocationRequestRepository repository, LocationRepository locations, Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.clock = clock;
    }

    @Transactional
    public RelocationCreateResponse create(RelocationCreateRequest req) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<String> codes = validate(req, now.toLocalDate());
        String reason = req.reason().trim(), requestedBy = req.requestedBy().trim();

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
        String requestNo = requestNo(now.getYear(), repository.maxRequestSeq(now.getYear()));
        long nextId = table.idIdentity() ? 0 : repository.maxId() + 1;
        List<RelocationHistoryRow> history = new ArrayList<>();
        for (RelocationAsset a : moving) {
            String group = clean(a.group()), pic = clean(a.pic());
            history.add(new RelocationHistoryRow(table.idIdentity() ? null : nextId++, requestNo, a.code(),
                    normalize(a.positionA()), normalizeSub(a.positionA(), a.positionAA()), normalize(a.positionAAA()), group, pic,
                    toA, toAA, null, group, pic,
                    req.plannedMoveDate(), req.plannedDoneDate(), now, requestedBy, reason, PENDING_PE, null));
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
        return new RelocationCreateResponse(requestNo, PENDING_PE, items, skipped);
    }

    @Transactional(readOnly = true)
    public RelocationRequestPage list(String status, String machineCode, String requestedBy, Integer page, Integer size) {
        String st = clean(status);
        if (st != null && !st.startsWith(STATUS_PREFIX)) throw new BadRequestException("status phải bắt đầu bằng " + STATUS_PREFIX + ".");
        int p = page == null ? 0 : page, s = size == null ? 20 : size;
        if (p < 0 || s < 1 || s > MAX_PAGE_SIZE) throw new BadRequestException("page ≥ 0, 1 ≤ size ≤ " + MAX_PAGE_SIZE + ".");
        Filter f = new Filter(st, clean(machineCode), clean(requestedBy));
        long total = repository.countRequests(f);
        List<String> nos = total == 0 ? List.of() : repository.findRequestNos(f, p * s, s);
        Map<String, List<RelocationHistoryRow>> byNo = repository.findRows(nos).stream()
                .collect(Collectors.groupingBy(RelocationHistoryRow::requestNo));
        List<RelocationRequestResponse> items = nos.stream().filter(byNo::containsKey).map(no -> toResponse(no, byNo.get(no))).toList();
        return new RelocationRequestPage(items, p, s, total);
    }

    @Transactional(readOnly = true)
    public RelocationRequestResponse get(String requestNo) {
        List<RelocationHistoryRow> rows = repository.findRows(List.of(requestNo));
        if (rows.isEmpty()) throw new NotFoundException("Không tìm thấy yêu cầu di dời: " + requestNo);
        return toResponse(requestNo, rows);
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
        if (clean(req.requestedBy()) == null) throw new BadRequestException("requestedBy là bắt buộc.");
        if (req.plannedMoveDate() == null || req.plannedDoneDate() == null) throw new BadRequestException("plannedMoveDate và plannedDoneDate là bắt buộc.");
        if (req.plannedMoveDate().isBefore(today)) throw new BadRequestException("plannedMoveDate không được trước hôm nay.");
        if (req.plannedDoneDate().isBefore(req.plannedMoveDate())) throw new BadRequestException("plannedDoneDate không được trước plannedMoveDate.");
        return List.copyOf(codes);
    }

    /** MAP row of the destination (exact A/AA after normalization), preferring the lowest Id; Outside is never one. */
    /** Fac of the destination MAP row (exact A/AA, not Outside, lowest Id), or null when there is none. */
    static String destinationFac(String a, String aa, List<LocationMapRow> rows) {
        String na = normalize(a);
        if (na == null) return null;
        String naa = normalizeSub(a, aa);
        return rows.stream()
                .filter(r -> na.equals(normalize(r.a())) && Objects.equals(naa, normalizeSub(r.a(), r.aa())) && !sameText(r.fac(), OUTSIDE_FAC))
                .min(Comparator.comparingLong(LocationMapRow::id)).map(LocationMapRow::fac).orElse(null);
    }

    static LocationMapRow destination(String a, String aa, List<LocationMapRow> rows) {
        List<LocationMapRow> same = rows.stream()
                .filter(r -> a.equals(normalize(r.a())) && Objects.equals(aa, normalizeSub(r.a(), r.aa())))
                .toList();
        String zone = aa != null ? aa : a;
        if (same.isEmpty()) throw new BadRequestException("Vị trí đích không có trong F2_FIXED_ASSET_MAP: " + zone);
        return same.stream().filter(r -> !sameText(r.fac(), OUTSIDE_FAC)).min(Comparator.comparingLong(LocationMapRow::id))
                .orElseThrow(() -> new BadRequestException("Không thể di dời đến vị trí Outside: " + zone));
    }

    static String requestNo(int year, Integer maxSeq) {
        int next = (maxSeq == null ? 0 : maxSeq) + 1;
        return "RL-%d-%04d".formatted(year, next);
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

    private static RelocationRequestResponse toResponse(String requestNo, List<RelocationHistoryRow> rows) {
        RelocationHistoryRow first = rows.get(0);
        Set<String> statuses = rows.stream().map(RelocationHistoryRow::status).collect(Collectors.toSet());
        List<RelocationRequestResponse.Item> items = rows.stream()
                .map(r -> new RelocationRequestResponse.Item(r.machineCode(),
                        new RelocationPosition(r.positionABf(), r.positionAABf(), r.positionAAABf()),
                        new RelocationPosition(r.positionAAt(), r.positionAAAt(), r.positionAAAAt()), r.groupBf(), r.picBf(), r.status()))
                .toList();
        return new RelocationRequestResponse(requestNo, statuses.size() == 1 ? first.status() : null, first.creater(), first.note(),
                first.plannedMoveDate(), first.plannedDoneDate(), first.createDate(),
                new RelocationPosition(first.positionAAt(), first.positionAAAt(), first.positionAAAAt()), drawingUrl(rows), items);
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
