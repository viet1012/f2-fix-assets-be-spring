package com.spc.fixedasset.model;

/** Asset columns needed for location matching (PositionA/AA not yet normalized). */
public record LocationAsset(
        String code,
        String name,
        String kind,
        String faType,
        String status,
        String div,
        String factory,
        String floor,
        String positionA,
        String positionAA
) {}
