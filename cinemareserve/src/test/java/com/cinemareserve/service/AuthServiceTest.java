package com.cinemareserve.service;

import com.cinemareserve.TestDb;
import com.cinemareserve.dao.UserDao;
import com.cinemareserve.domain.Role;
import com.cinemareserve.domain.User;
import com.cinemareserve.exception.AuthenticationException;
import com.cinemareserve.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        TestDb.freshDatabase();
        authService = new AuthService(new UserDao());
    }

    @Test
    void registerCreatesUserWithHashedPassword() {
        User user = authService.register("bob99", "bob@example.com", "SecurePass1");
        assertNotNull(user.getId());
        assertEquals(Role.USER, user.getRole());
        assertNotEquals("SecurePass1", user.getPasswordHash(), "password must never be stored in plain text");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        authService.register("bob99", "bob@example.com", "SecurePass1");
        assertThrows(ValidationException.class,
                () -> authService.register("bob99", "different@example.com", "AnotherPass1"));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        authService.register("bob99", "bob@example.com", "SecurePass1");
        assertThrows(ValidationException.class,
                () -> authService.register("bobby", "bob@example.com", "AnotherPass1"));
    }

    @Test
    void registerRejectsShortPassword() {
        assertThrows(ValidationException.class,
                () -> authService.register("shortpw", "short@example.com", "abc123"));
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        authService.register("carol", "carol@example.com", "CarolPass1");
        User loggedIn = authService.login("carol", "CarolPass1");
        assertEquals("carol", loggedIn.getUsername());
    }

    @Test
    void loginFailsWithWrongPassword() {
        authService.register("carol", "carol@example.com", "CarolPass1");
        assertThrows(AuthenticationException.class, () -> authService.login("carol", "WrongPassword"));
    }

    @Test
    void loginFailsForUnknownUsername() {
        assertThrows(AuthenticationException.class, () -> authService.login("ghost", "whatever1"));
    }
}
