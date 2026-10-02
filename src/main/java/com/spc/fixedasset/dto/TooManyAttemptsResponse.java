package com.spc.fixedasset.dto;

public record TooManyAttemptsResponse(String error, long retryAfterSeconds) {}
