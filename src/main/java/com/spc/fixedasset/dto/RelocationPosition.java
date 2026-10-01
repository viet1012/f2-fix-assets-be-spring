package com.spc.fixedasset.dto;

/** Normalized zone (LocationMatcher); positionAAA is only set for snapshots (_BF). */
public record RelocationPosition(String positionA, String positionAA, String positionAAA) {}
