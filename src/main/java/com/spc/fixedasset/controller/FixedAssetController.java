package com.spc.fixedasset.controller;

import com.spc.fixedasset.dto.*;
import com.spc.fixedasset.exception.ImportException;
import com.spc.fixedasset.service.FixedAssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FixedAssetController {

    private final FixedAssetService service;

    public FixedAssetController(FixedAssetService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = service.health();
        return response.ok()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/assets")
    public AssetsResponse getAssets() {
        return service.getAssets();
    }

    @GetMapping("/import-history")
    public List<ImportHistoryResponse> getImportHistory() {
        return service.getImportHistory();
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public UploadResponse upload(@RequestPart(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportException("Không có file nào được gửi lên (missing \"file\" field).");
        }
        try {
            return service.importUpload(file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new ImportException("Không thể đọc file upload.", e);
        }
    }

    @PostMapping(value = "/upload-from-url", consumes = "application/json")
    public UploadResponse uploadFromUrl(@RequestBody UrlImportRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            throw new ImportException("Missing url in request body.");
        }
        return service.importUrl(request.url());
    }
}
