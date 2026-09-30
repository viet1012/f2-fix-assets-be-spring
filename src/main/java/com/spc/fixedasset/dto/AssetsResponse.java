package com.spc.fixedasset.dto;

import com.spc.fixedasset.model.FixedAsset;
import java.util.List;

public record AssetsResponse(
        List<FixedAsset> tableData,
        FixedAssetFiltersResponse filters,
        LastImportResponse lastImport
) {}
