package com.spc.fixedasset.dto;

import java.util.List;

/** page is 0-based; total counts requests (RequestNo), not rows. */
public record RelocationRequestPage(List<RelocationRequestResponse> items, int page, int size, long total) {}
