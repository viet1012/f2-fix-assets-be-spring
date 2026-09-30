package com.spc.fixedasset.dto;

import com.spc.fixedasset.service.LocationMatcher.MatchLevel;

/** positionA/positionAA are normalized; map* and aPos/aaPos are null when matchLevel is NONE. */
public record AssetLocationResponse(
        String code,
        String name,
        String kind,
        String faType,
        String status,
        String div,
        String factory,
        String floor,
        String positionA,
        String positionAA,
        String currentZone,
        Long mapId,
        String mapFac,
        String mapFloor,
        Object aPos,
        Object aaPos,
        MatchLevel matchLevel,
        boolean floorMismatch
) {}
