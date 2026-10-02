package com.spc.fixedasset.controller;

import com.spc.fixedasset.auth.AuthSession;
import com.spc.fixedasset.dto.DrawingUploadResponse;
import com.spc.fixedasset.exception.BadRequestException;
import com.spc.fixedasset.exception.ForbiddenException;
import com.spc.fixedasset.service.RelocationDrawingService;
import com.spc.fixedasset.service.RelocationRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** PNG drawing of a relocation request (overwritten on re-upload); only its creator may upload. */
@RestController
@RequestMapping("/api/relocation-requests")
public class RelocationDrawingController {

    private final RelocationDrawingService service;
    private final RelocationRequestService requests;

    public RelocationDrawingController(RelocationDrawingService service, RelocationRequestService requests) {
        this.service = service;
        this.requests = requests;
    }

    @PostMapping(value = "/{requestNo}/drawing", consumes = "multipart/form-data")
    public DrawingUploadResponse upload(@PathVariable String requestNo,
                                        @RequestPart(value = "file", required = false) MultipartFile file,
                                        HttpServletRequest http) {
        String account = AuthSession.requireAccount(http);
        String creator = requests.creatorOf(requestNo);
        // Accounts compare case-insensitively, like the SQL Server collation used at login.
        if (creator == null || !creator.trim().equalsIgnoreCase(account.trim())) {
            throw new ForbiddenException("Chỉ người tạo yêu cầu mới được tải bản vẽ lên.");
        }
        if (file == null || file.isEmpty()) throw new BadRequestException("Thiếu file bản vẽ (missing \"file\" field).");
        if (file.getSize() > RelocationDrawingService.MAX_BYTES) throw new BadRequestException("Bản vẽ vượt quá 10MB.");
        try {
            return service.upload(requestNo, file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Không thể đọc file upload.");
        }
    }
}
