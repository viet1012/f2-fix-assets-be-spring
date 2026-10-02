package com.spc.fixedasset.dto;

/** password is never trimmed or case-folded; toString hides it so it cannot reach a log. */
public record LoginRequest(String account, String password) {
    @Override
    public String toString() {
        return "LoginRequest[account=" + account + ", password=***]";
    }
}
