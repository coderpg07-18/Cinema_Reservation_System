package com.cinemareserve.service;

import com.cinemareserve.domain.User;
import com.cinemareserve.exception.AuthorizationException;

/** Centralizes the "is this user allowed to do this" check so every admin-only
 *  service method enforces it the same way instead of re-implementing it. */
final class AuthorizationGuard {

    private AuthorizationGuard() {
    }

    static void requireAdmin(User actor) {
        if (actor == null) {
            throw new AuthorizationException("authentication required");
        }
        if (!actor.isAdmin()) {
            throw new AuthorizationException("admin privileges required for this operation");
        }
    }
}
