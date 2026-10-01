package com.spc.fixedasset.controller;

import com.spc.fixedasset.dto.RelocationCreateRequest;
import com.spc.fixedasset.dto.RelocationCreateResponse;
import com.spc.fixedasset.dto.RelocationRequestPage;
import com.spc.fixedasset.dto.RelocationRequestResponse;
import com.spc.fixedasset.service.RelocationRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Relocation requests stored in F2_FIXED_ASSET_HISTORY (Status REQ_*). */
@RestController
@RequestMapping("/api/relocation-requests")
public class RelocationRequestController {

    private final RelocationRequestService service;

    public RelocationRequestController(RelocationRequestService service) {
        this.service = service;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<RelocationCreateResponse> create(@RequestBody RelocationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
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
