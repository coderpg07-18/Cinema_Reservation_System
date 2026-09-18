package com.cinemareserve.security;

import org.mindrot.jbcrypt.BCrypt;

/** Thin wrapper so the rest of the codebase never touches BCrypt directly. */
public final class PasswordHasher {

    private static final int WORK_FACTOR = 12;

    private PasswordHasher() {
    }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean matches(String plainPassword, String storedHash) {
        return BCrypt.checkpw(plainPassword, storedHash);
    }
}
