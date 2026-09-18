package com.cinemareserve.exception;

/** Thrown when user-supplied input fails business validation rules. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
