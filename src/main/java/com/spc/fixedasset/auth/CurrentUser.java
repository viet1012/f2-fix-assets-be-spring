package com.spc.fixedasset.auth;

import java.io.Serializable;

/** Logged-in user kept in the session and returned by /api/auth/login and /me; HR fields are null when unknown. */
public record CurrentUser(String account, String name, String dept, String section) implements Serializable {}
