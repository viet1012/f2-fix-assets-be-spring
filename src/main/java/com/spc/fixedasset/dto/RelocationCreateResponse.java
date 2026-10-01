package com.spc.fixedasset.dto;

import java.util.List;

/** skipped: machine codes already at the destination (no row written). */
public record RelocationCreateResponse(String requestNo, String status, List<Item> items, List<String> skipped) {
    /** moveType: building (MAP fac differs), floor (MAP floor differs) or same. */
    public record Item(String machineCode, RelocationPosition from, RelocationPosition to, String moveType) {}
}
