package com.chubb.claims.common;

/**
 * Thrown when a request breaks a business rule (e.g. incident date in the future, reserve above coverage limit).
 * The message is written to be shown to the caller.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
