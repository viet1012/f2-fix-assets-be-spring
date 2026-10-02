package com.spc.fixedasset.auth;

/** Trimmed values from dbo.F2_HR_Data; resigned = Date_of_resign is set and not after today. */
public record HrProfile(String name, String dept, String section, boolean resigned) {}
