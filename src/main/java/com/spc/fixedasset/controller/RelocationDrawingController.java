package com.spc.fixedasset.controller;

import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.service.RelocationDrawingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** PNG drawing of a relocation request (overwritten on re-upload). */
@RestController
@RequestMapping("/api/relocation-requests")
public class RelocationDrawingController {

    private final RelocationDrawingService service;

    public RelocationDrawingController(RelocationDrawingService service) {
        this.service = service;
    }

    @PostMapping(value = "/{requestNo}/drawing", consumes = "multipart/form-data")
    public DrawingUploadResponse upload(@PathVariable String requestNo,
                                        @RequestPart(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Thiếu file bản vẽ (missing \"file\" field).");
        if (file.getSize() > RelocationDrawingService.MAX_BYTES) throw new BadRequestException("Bản vẽ vượt quá 10MB.");
        try {
            return service.upload(requestNo, file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc file upload.");
        }
    }
}
