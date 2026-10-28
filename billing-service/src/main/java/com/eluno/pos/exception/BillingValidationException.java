package com.eluno.pos.exception;

/** A readable business-rule rejection returned as HTTP 422. */
public class BillingValidationException extends RuntimeException {
    public BillingValidationException(String message) {
        super(message);
    }
}
