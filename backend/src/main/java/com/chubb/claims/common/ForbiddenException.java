package com.chubb.claims.common;

/** 403 Forbidden: the caller is known but not allowed to do this. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
