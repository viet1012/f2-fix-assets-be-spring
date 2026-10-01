package com.spc.fixedasset.exception;

import java.util.List;

/** 409; codes are the machine codes in conflict (may be empty when the database does not say which). */
public class ConflictException extends RuntimeException {
    private final List<String> codes;

    public ConflictException(String message, List<String> codes) {
        super(message);
        this.codes = List.copyOf(codes);
    }

    public List<String> codes() {
        return codes;
    }
}
