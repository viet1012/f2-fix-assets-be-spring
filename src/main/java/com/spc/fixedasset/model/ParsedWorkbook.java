package com.spc.fixedasset.model;

import java.util.List;

public record ParsedWorkbook(String sheetName, List<ImportedFixedAsset> rows) {}
