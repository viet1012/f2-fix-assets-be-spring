package com.spc.fixedasset.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/** Values parsed from one Excel row plus the DB columns actually present in the workbook. */
public record ImportedFixedAsset(
        String machineCode,
        String faName,
        String physicalCount,
        String faType,
        String kindExpense,
        String kindFixedAsset,
        LocalDateTime dateStart,
        BigDecimal histotyCost,
        BigDecimal netBookValue,
        BigDecimal yearDepreciation,
        String group,
        String emailChecked,
        String emailApproved,
        String div,
        String factory,
        String floor,
        String positionA,
        String positionAA,
        String positionAAA,
        String invoiceNo,
        String maker,
        String purchasedFrom,
        String type,
        String serialNo,
        String checkMETI,
        String photo1,
        String photo2,
        String photo3,
        String photoJudge,
        String machineStatus,
        Set<String> presentColumns
) {
    public boolean has(String column) {
        return presentColumns.contains(column);
    }
}
