package com.spc.fixedasset.model;

/** Raw row of dbo.F2_FIXED_ASSET_MAP (A/AA not yet normalized). */
public record LocationMapRow(
        long id,
        String fac,
        String div,
        String floor,
        String a,
        String aa,
        String aPos,
        String aaPos
) {}
