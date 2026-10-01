package com.spc.fixedasset.dto;

import java.util.List;

public record ApiConflictResponse(String error, List<String> codes) {}
