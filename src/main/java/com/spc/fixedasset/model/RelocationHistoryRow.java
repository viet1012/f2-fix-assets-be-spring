package com.spc.fixedasset.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One row of dbo.F2_FIXED_ASSET_HISTORY written by the relocation request feature (Status starts with REQ_). */
public record RelocationHistoryRow(
        Long id,
        String requestNo,
        String machineCode,
        String positionABf,
        String positionAABf,
        String positionAAABf,
        String groupBf,
        String picBf,
        String positionAAt,
        String positionAAAt,
        String positionAAAAt,
        String groupAt,
        String picAt,
        LocalDate plannedMoveDate,
        LocalDate plannedDoneDate,
        LocalDateTime createDate,
        String creater,
        String note,
        String status,
        /** webUrl of the drawing, or its file name when there is no base URL or the URL does not fit the column. */
        String drawings
) {}
