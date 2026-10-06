package com.ecoloop.common;

public class RateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds, String message) {
        super(message);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
