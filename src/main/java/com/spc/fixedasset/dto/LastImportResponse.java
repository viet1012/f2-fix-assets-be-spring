package com.spc.fixedasset.dto;

import java.time.LocalDateTime;

public record LastImportResponse(
        Long id,
        String source_name,
        String sheet_name,
        Integer row_count,
        LocalDateTime imported_at
) {}
