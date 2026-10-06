package com.spc.fixedasset.service;

import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.NotFoundException;
import com.spc.fixedasset.model.RelocationHistoryRow;
import com.spc.fixedasset.repository.RelocationRequestRepository;
import com.spc.fixedasset.storage.DrawingStorage;
import com.spc.fixedasset.storage.DrawingStorage.StoredDrawing;
import com.spc.fixedasset.config.DrawingStorageConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** PNG drawing of a relocation request: saved via DrawingStorage, its webUrl (or file name) kept in Drawings; the Excel export is then rewritten. */
@Service
public class RelocationDrawingService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final RelocationRequestRepository repository;
    private final DrawingStorage storage;
    private final RelocationExcelService excel;
    /** Zone CreateDate was written in (the app writes LocalDateTime.now() of the JVM). */
    private final ZoneId createDateZone;

    @Autowired
    public RelocationDrawingService(RelocationRequestRepository repository, @Qualifier(DrawingStorageConfig.DRAWINGS) DrawingStorage storage,
                                    RelocationExcelService excel) {
        this(repository, storage, excel, ZoneId.systemDefault());
    }

    RelocationDrawingService(RelocationRequestRepository repository, DrawingStorage storage, RelocationExcelService excel, ZoneId createDateZone) {
        this.repository = repository;
        this.storage = storage;
        this.excel = excel;
        this.createDateZone = createDateZone;
    }

    /** 404 unknown request, 400 not a PNG / too large, 503 storage unusable; re-uploading overwrites the same file. */
    public DrawingUploadResponse upload(String requestNo, byte[] content) {
        List<RelocationHistoryRow> rows = repository.findRows(List.of(requestNo));
        if (rows.isEmpty()) throw new NotFoundException("Không tìm thấy yêu cầu di dời: " + requestNo);
        validatePng(content);

        String name = RelocationFileNames.baseName(requestNo, rows.get(0).createDate(), createDateZone) + ".png";

        StoredDrawing stored;
        try {
            stored = storage.save(name, content, DrawingStorage.PNG);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        Integer max = repository.tableInfo().maxLengths().get("drawings");
        String url = stored.webUrl();
        String value = url != null && (max == null || max < 0 || url.length() <= max) ? url : stored.fileName();
        repository.updateDrawings(requestNo, value);
        // DrawingFile column of the export; a failure is only logged, the drawing is already saved.
        excel.tryExport(requestNo);
        return new DrawingUploadResponse(stored.fileName(), url);
    }

    static void validatePng(byte[] content) {
        if (content == null || content.length == 0) throw new BadRequestException("Thiếu file bản vẽ.");
        if (content.length > MAX_BYTES) throw new BadRequestException("Bản vẽ vượt quá 10MB.");
        if (content.length < PNG_MAGIC.length || !Arrays.equals(Arrays.copyOf(content, PNG_MAGIC.length), PNG_MAGIC)) {
            throw new BadRequestException("Bản vẽ phải là file PNG.");
        }
    }
}
