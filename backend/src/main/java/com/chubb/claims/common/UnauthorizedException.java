package com.chubb.claims.common;

/** 401 Unauthorized: missing, unknown or inconsistent identity headers. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
