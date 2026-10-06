package com.spc.fixedasset.dto;

/** webUrl is null when drawings.base-url is not set. */
public record ExcelExportResponse(String fileName, String webUrl) {}
