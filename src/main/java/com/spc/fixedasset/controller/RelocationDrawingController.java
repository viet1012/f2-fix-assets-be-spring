package com.spc.fixedasset.controller;

import com.spc.fixedasset.auth.AuthSession;
import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.dto.ExcelExportResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.ForbiddenException;
import com.spc.fixedasset.service.RelocationDrawingService;
import com.spc.fixedasset.service.RelocationExcelService;
import com.spc.fixedasset.service.RelocationRequestService;
import com.spc.fixedasset.storage.DrawingStorage.StoredDrawing;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Files of a relocation request: PNG drawing (overwritten on re-upload) and the Excel export; only its creator may write them. */
@RestController
@RequestMapping("/api/relocation-requests")
public class RelocationDrawingController {

    private final RelocationDrawingService service;
    private final RelocationRequestService requests;
    private final RelocationExcelService excel;

    public RelocationDrawingController(RelocationDrawingService service, RelocationRequestService requests, RelocationExcelService excel) {
        this.service = service;
        this.requests = requests;
        this.excel = excel;
    }

    @PostMapping(value = "/{requestNo}/drawing", consumes = "multipart/form-data")
    public DrawingUploadResponse upload(@PathVariable String requestNo,
                                        @RequestPart(value = "file", required = false) MultipartFile file,
                                        HttpServletRequest http) {
        requireCreator(requestNo, http, "Chỉ người tạo yêu cầu mới được tải bản vẽ lên.");
        if (file == null || file.isEmpty()) throw new BadRequestException("Thiếu file bản vẽ (missing \"file\" field).");
        if (file.getSize() > RelocationDrawingService.MAX_BYTES) throw new BadRequestException("Bản vẽ vượt quá 10MB.");
        try {
            return service.upload(requestNo, file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc file upload.");
        }
    }

    /** Rewrites {RequestNo}_{yyMMdd-HHmmss}.xlsx from the stored rows. */
    @PostMapping("/{requestNo}/excel")
    public ExcelExportResponse exportExcel(@PathVariable String requestNo, HttpServletRequest http) {
        requireCreator(requestNo, http, "Chỉ người tạo yêu cầu mới được tạo lại file Excel.");
        StoredDrawing stored = excel.export(requestNo);
        return new ExcelExportResponse(stored.fileName(), stored.webUrl());
    }

    private void requireCreator(String requestNo, HttpServletRequest http, String forbidden) {
        String account = AuthSession.requireAccount(http);
        String creator = requests.creatorOf(requestNo);
        // Accounts compare case-insensitively, like the SQL Server collation used at login.
        if (creator == null || !creator.trim().equalsIgnoreCase(account.trim())) throw new ForbiddenException(forbidden);
    }
}
