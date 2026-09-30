package com.spc.fixedasset.dto;

public record HealthResponse(boolean ok, String db, String error) {
    public static HealthResponse connected() {
        return new HealthResponse(true, "connected", null);
    }

    public static HealthResponse unavailable(String error) {
        return new HealthResponse(false, "disconnected", error);
    }
}
