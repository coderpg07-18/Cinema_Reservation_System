package com.cinemareserve.util;

import java.security.SecureRandom;

/** Generates short, human-readable booking references, e.g. CR-7F3K9Q. */
public final class BookingReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I ambiguity
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReferenceGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("CR-");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
