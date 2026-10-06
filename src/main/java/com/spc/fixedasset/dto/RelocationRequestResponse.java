package com.spc.fixedasset.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One request = every F2_FIXED_ASSET_HISTORY row sharing its RequestNo; status is null when the rows disagree. */
public record RelocationRequestResponse(
        String requestNo,
        String status,
        /** Creater as stored: "{account}_{name}", or only the account for old rows. */
        String requestedBy,
        /** Account part of Creater. */
        String requesterCode,
        /** Name part of Creater; for old rows F2_HR_Data.Name of the account; null when unknown. */
        String requesterName,
        String reason,
        LocalDate plannedMoveDate,
        LocalDate plannedDoneDate,
        LocalDateTime createdAt,
        RelocationPosition to,
        /** Drawings as stored: webUrl of the drawing, or its file name; null when none was uploaded. */
        String drawingUrl,
        /** Excel export: web link (drawings.excel-base-url) or its file name; the file may not exist yet. */
        String excelUrl,
        List<Item> items
) {
    /** Snapshot per machine: from = *_BF, to = *_AT columns of its row. */
    public record Item(String machineCode, RelocationPosition from, RelocationPosition to, String group, String pic, String status) {}
}
