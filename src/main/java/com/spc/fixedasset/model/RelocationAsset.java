package com.spc.fixedasset.model;

/** F2_FIXED_ASSET columns snapshotted into _BF (raw, not normalized); pic = EmailChecked. */
public record RelocationAsset(
        String code,
        String kind,
        String div,
        String floor,
        String positionA,
        String positionAA,
        String positionAAA,
        String group,
        String pic
) {}
