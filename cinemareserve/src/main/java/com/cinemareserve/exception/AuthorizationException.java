package com.cinemareserve.exception;

/** Thrown when an authenticated user attempts an action outside their role's permissions. */
public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }
}
