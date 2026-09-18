package com.cinemareserve.exception;

/** Thrown when an operation is attempted against a reservation in an incompatible state,
 *  e.g. cancelling a reservation that is already CANCELLED, or confirming an EXPIRED hold. */
public class InvalidReservationStateException extends RuntimeException {
    public InvalidReservationStateException(String message) {
        super(message);
    }
}
