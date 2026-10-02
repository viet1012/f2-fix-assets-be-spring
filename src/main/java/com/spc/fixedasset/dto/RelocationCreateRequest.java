package com.spc.fixedasset.dto;

import java.time.LocalDate;
import java.util.List;

public record RelocationCreateRequest(
        List<String> machineCodes,
        Target to,
        LocalDate plannedMoveDate,
        LocalDate plannedDoneDate,
        String reason
) {
    public record Target(String positionA, String positionAA) {}
}
