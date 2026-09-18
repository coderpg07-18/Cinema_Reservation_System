package com.cinemareserve.util;

import com.cinemareserve.exception.ValidationException;

import java.util.regex.Pattern;

/** Small collection of reusable input-validation checks shared across services. */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private Validator() {
    }

    public static void notBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " must not be empty");
        }
    }

    public static void positive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(fieldName + " must be greater than zero");
        }
    }

    public static void notNegative(double value, String fieldName) {
        if (value < 0) {
            throw new ValidationException(fieldName + " must not be negative");
        }
    }

    public static void validEmail(String email) {
        notBlank(email, "email");
        if (!EMAIL.matcher(email).matches()) {
            throw new ValidationException("email is not a valid address: " + email);
        }
    }

    public static void minLength(String value, int min, String fieldName) {
        if (value == null || value.length() < min) {
            throw new ValidationException(fieldName + " must be at least " + min + " characters");
        }
    }
}
