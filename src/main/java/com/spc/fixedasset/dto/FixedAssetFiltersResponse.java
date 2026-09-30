package com.spc.fixedasset.dto;

import java.util.List;

public record FixedAssetFiltersResponse(
        List<String> groups,
        List<String> checked,
        List<String> approved,
        List<String> div,
        List<String> factory,
        List<String> floor,
        List<String> kind,
        List<String> status
) {}
