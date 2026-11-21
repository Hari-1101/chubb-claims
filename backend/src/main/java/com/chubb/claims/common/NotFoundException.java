package com.chubb.claims.common;

/** 404 Not Found: the resource doesn't exist (or the caller may not know it exists). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
