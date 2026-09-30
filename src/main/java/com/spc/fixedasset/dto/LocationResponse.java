package com.spc.fixedasset.dto;

/** aPos/aaPos: number when the MAP value is numeric, otherwise the original string. */
public record LocationResponse(
        long id,
        String fac,
        String div,
        String floor,
        String positionA,
        String positionAA,
        Object aPos,
        Object aaPos,
        int assetCount
) {}
