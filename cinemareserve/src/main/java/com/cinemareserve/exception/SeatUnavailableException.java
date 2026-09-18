package com.cinemareserve.exception;

/** Thrown when one or more requested seats are already held/confirmed for a showtime. */
public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) {
        super(message);
    }
}
