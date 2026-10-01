package com.spc.fixedasset.dto;

/** webUrl is null when drawings.base-url is not set. */
public record DrawingUploadResponse(String fileName, String webUrl) {}
