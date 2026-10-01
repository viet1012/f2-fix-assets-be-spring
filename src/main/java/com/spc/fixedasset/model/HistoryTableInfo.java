package com.spc.fixedasset.model;

import java.util.Map;

/** Live metadata of F2_FIXED_ASSET_HISTORY: max length per character column (-1 = MAX) and whether Id is IDENTITY. */
public record HistoryTableInfo(Map<String, Integer> maxLengths, boolean idIdentity) {}
