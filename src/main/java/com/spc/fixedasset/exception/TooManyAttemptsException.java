package com.spc.fixedasset.exception;

/** 429: the account is locked after too many failed logins. */
public class TooManyAttemptsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
