package com.eluno.pos.exception;

/** Distinguishes a rejected request from a write whose outcome is unknown. */
public class UpstreamApiException extends RuntimeException {
    private final boolean outcomeUncertain;

    public UpstreamApiException(String message, boolean outcomeUncertain) {
        super(message);
        this.outcomeUncertain = outcomeUncertain;
    }

    public boolean isOutcomeUncertain() {
        return outcomeUncertain;
    }
}
