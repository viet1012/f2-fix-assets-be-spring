package com.spc.fixedasset.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One request = every F2_FIXED_ASSET_HISTORY row sharing its RequestNo; status is null when the rows disagree. */
public record RelocationRequestResponse(
        String requestNo,
        String status,
        String requestedBy,
        /** F2_HR_Data.Name of requestedBy (Creater); null when not found. */
        String requesterName,
        String reason,
        LocalDate plannedMoveDate,
        LocalDate plannedDoneDate,
        LocalDateTime createdAt,
        RelocationPosition to,
        /** Drawings as stored: webUrl of the drawing, or its file name; null when none was uploaded. */
        String drawingUrl,
        List<Item> items
) {
    /** Snapshot per machine: from = *_BF, to = *_AT columns of its row. */
    public record Item(String machineCode, RelocationPosition from, RelocationPosition to, String group, String pic, String status) {}
}
