package com.cinemareserve.exception;

/** Thrown when login credentials are invalid or the account is inactive. */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
