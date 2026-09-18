package com.cinemareserve.exception;

/** Thrown when a new showtime would overlap an existing showtime on the same screen. */
public class ScheduleConflictException extends RuntimeException {
    public ScheduleConflictException(String message) {
        super(message);
    }
}
