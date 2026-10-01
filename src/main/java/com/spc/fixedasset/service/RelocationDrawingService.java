package com.spc.fixedasset.service;

import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.model.RelocationHistoryRow;
import com.spc.fixedasset.repository.LocationRepository;
import com.spc.fixedasset.repository.RelocationRequestRepository;
import com.spc.fixedasset.storage.DrawingStorage;
import com.spc.fixedasset.storage.DrawingStorage.StoredDrawing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/** PNG drawing of a relocation request: saved via DrawingStorage, its webUrl (or file name) kept in Drawings. */
@Service
public class RelocationDrawingService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    static final ZoneId FILE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");

    private final RelocationRequestRepository repository;
    private final LocationRepository locations;
    private final DrawingStorage storage;
    /** Zone CreateDate was written in (the app writes LocalDateTime.now() of the JVM). */
    private final ZoneId createDateZone;

    @Autowired
    public RelocationDrawingService(RelocationRequestRepository repository, LocationRepository locations, DrawingStorage storage) {
        this(repository, locations, storage, ZoneId.systemDefault());
    }

    RelocationDrawingService(RelocationRequestRepository repository, LocationRepository locations, DrawingStorage storage, ZoneId createDateZone) {
        this.repository = repository;
        this.locations = locations;
        this.storage = storage;
        this.createDateZone = createDateZone;
    }

    /** 404 unknown request, 400 not a PNG / too large, 503 storage unusable; re-uploading overwrites the same file. */
    public DrawingUploadResponse upload(String requestNo, byte[] content) {
        List<RelocationHistoryRow> rows = repository.findRows(List.of(requestNo));
        if (rows.isEmpty()) throw new NotFoundException("Không tìm thấy yêu cầu di dời: " + requestNo);
        validatePng(content);

        RelocationHistoryRow first = rows.get(0);
        String fac = RelocationRequestService.destinationFac(first.positionAAt(), first.positionAAAt(), locations.findMapRows());
        String area = first.positionAAAt() != null ? first.positionAAAt() : first.positionAAt();
        String name = fileName(first.createDate(), createDateZone, requestNo, fac, area);

        StoredDrawing stored;
        try {
            stored = storage.save(name, content);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        Integer max = repository.tableInfo().maxLengths().get("drawings");
        String url = stored.webUrl();
        String value = url != null && (max == null || max < 0 || url.length() <= max) ? url : stored.fileName();
        repository.updateDrawings(requestNo, value);
        return new DrawingUploadResponse(stored.fileName(), url);
    }

    static void validatePng(byte[] content) {
        if (content == null || content.length == 0) throw new BadRequestException("Thiếu file bản vẽ.");
        if (content.length > MAX_BYTES) throw new BadRequestException("Bản vẽ vượt quá 10MB.");
        if (content.length < PNG_MAGIC.length || !Arrays.equals(Arrays.copyOf(content, PNG_MAGIC.length), PNG_MAGIC)) {
            throw new BadRequestException("Bản vẽ phải là file PNG.");
        }
    }

    /** yyMMdd-HHmmss_{RequestNo}_{Fac}_{Area}.png, time in Asia/Ho_Chi_Minh; missing parts become "NA". */
    static String fileName(LocalDateTime createDate, ZoneId createDateZone, String requestNo, String fac, String area) {
        String stamp = createDate.atZone(createDateZone).withZoneSameInstant(FILE_ZONE).format(STAMP);
        return stamp + "_" + part(requestNo) + "_" + part(fac) + "_" + part(area) + ".png";
    }

    /** Windows/OneDrive reserved characters (\ / : * ? " < > | # %), controls and whitespace become "-"; no leading/trailing dots. */
    static String part(String v) {
        if (v == null || v.isBlank()) return "NA";
        String s = v.trim().replaceAll("[\\\\/:*?\"<>|#%\\p{Cntrl}\\s]", "-").replaceAll("^\\.+|\\.+$", "");
        return s.isEmpty() ? "NA" : s;
    }
}
