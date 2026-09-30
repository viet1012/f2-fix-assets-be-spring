package com.spc.fixedasset.dto;

import java.time.LocalDateTime;

public record ImportHistoryResponse(
        Long id,
        String source_type,
        String source_name,
        String sheet_name,
        Integer row_count,
        String status,
        String error_message,
        LocalDateTime imported_at
) {}
