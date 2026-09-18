package com.cinemareserve.exception;

/** Thrown when a requested entity (movie, showtime, seat, reservation, etc.) does not exist. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
