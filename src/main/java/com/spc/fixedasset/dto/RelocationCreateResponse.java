package com.spc.fixedasset.dto;

import java.util.List;

/**
 * skipped: machine codes already at the destination (no row written). excelFile/excelUrl: the Excel export written after
 * commit (excelUrl = web link, or the file name without drawings.excel-base-url), or excelError when it could not be
 * written (the request is created either way).
 */
public record RelocationCreateResponse(String requestNo, String status, List<Item> items, List<String> skipped,
                                       String excelFile, String excelUrl, String excelError) {

    public RelocationCreateResponse(String requestNo, String status, List<Item> items, List<String> skipped) {
        this(requestNo, status, items, skipped, null, null, null);
    }

    public RelocationCreateResponse withExcel(String excelFile, String excelUrl, String excelError) {
        return new RelocationCreateResponse(requestNo, status, items, skipped, excelFile, excelUrl, excelError);
    }

    /** moveType: building (MAP fac differs), floor (MAP floor differs) or same. */
    public record Item(String machineCode, RelocationPosition from, RelocationPosition to, String moveType) {}
}
