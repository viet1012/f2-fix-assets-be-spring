package com.spc.fixedasset.controller;

import com.spc.fixedasset.auth.AuthSession;
import com.spc.fixedasset.auth.CurrentUser;
import com.spc.fixedasset.dto.RelocationCreateRequest;
import com.spc.fixedasset.dto.RelocationCreateResponse;
import com.spc.fixedasset.dto.RelocationRequestPage;
import com.spc.fixedasset.dto.RelocationRequestResponse;
import com.spc.fixedasset.service.RelocationExcelService;
import com.spc.fixedasset.service.RelocationRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Relocation requests stored in F2_FIXED_ASSET_HISTORY (Status REQ_*). */
@RestController
@RequestMapping("/api/relocation-requests")
public class RelocationRequestController {

    private final RelocationRequestService service;
    private final RelocationExcelService excel;

    public RelocationRequestController(RelocationRequestService service, RelocationExcelService excel) {
        this.service = service;
        this.excel = excel;
    }

    /**
     * Creater is built from the logged-in session ("{account}_{name}"); any requestedBy/creater in the body is ignored. The Excel export runs after create()
     * has committed; failing to write it never undoes the request (excelError instead of excelFile).
     */
    @PostMapping(consumes = "application/json")
    public ResponseEntity<RelocationCreateResponse> create(@RequestBody RelocationCreateRequest request, HttpServletRequest http) {
        CurrentUser user = AuthSession.requireUser(http);
        RelocationCreateResponse created = service.create(request, user.account(), user.name());
        RelocationExcelService.Result file = excel.tryExport(created.requestNo());
        return ResponseEntity.status(HttpStatus.CREATED).body(created.withExcel(file.fileName(), file.url(), file.error()));
    }

    /** Grouped by RequestNo, newest first; page is 0-based. */
    @GetMapping
    public RelocationRequestPage list(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String machineCode,
                                      @RequestParam(required = false) String requestedBy,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        return service.list(status, machineCode, requestedBy, page, size);
    }

    @GetMapping("/{requestNo}")
    public RelocationRequestResponse get(@PathVariable String requestNo) {
        return service.get(requestNo);
    }
}
