package com.spc.fixedasset.model;

import java.math.BigDecimal;

public record FixedAsset(
        String code,
        String name,
        String assetGroup,
        String floor,
        String position,
        BigDecimal cost,
        String maker,
        String pic,
        String picApproved,
        String status,
        String kind,
        String div,
        String factory,
        BigDecimal depYears,
        String dateStart,
        String photoEval,
        boolean hasPhoto
) {}
