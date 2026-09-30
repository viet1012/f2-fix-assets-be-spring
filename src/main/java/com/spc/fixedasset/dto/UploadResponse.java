package com.spc.fixedasset.dto;

public record UploadResponse(
        boolean ok,
        String sheetName,
        int rowCount,
        long importId,
        String sourceName
) {}
