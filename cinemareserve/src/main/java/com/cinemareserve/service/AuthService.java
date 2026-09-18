package com.cinemareserve.service;

import com.cinemareserve.dao.UserDao;
import com.cinemareserve.db.Database;
import com.cinemareserve.domain.Role;
import com.cinemareserve.domain.User;
import com.cinemareserve.exception.AuthenticationException;
import com.cinemareserve.exception.ValidationException;
import com.cinemareserve.security.PasswordHasher;
import com.cinemareserve.util.Validator;

import java.sql.Connection;
import java.sql.SQLException;

public class AuthService {

    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public User register(String username, String email, String plainPassword) {
        Validator.notBlank(username, "username");
        Validator.minLength(username, 3, "username");
        Validator.validEmail(email);
        Validator.minLength(plainPassword, 8, "password");

        try (Connection conn = Database.getConnection()) {
            if (userDao.findByUsername(conn, username).isPresent()) {
                throw new ValidationException("username already taken: " + username);
            }
            if (userDao.findByEmail(conn, email).isPresent()) {
                throw new ValidationException("an account already exists for this email");
            }
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(PasswordHasher.hash(plainPassword));
            user.setRole(Role.USER);
            user.setActive(true);
            return userDao.insert(conn, user);
        } catch (SQLException e) {
            throw new IllegalStateException("registration failed", e);
        }
    }

    public User login(String username, String plainPassword) {
        Validator.notBlank(username, "username");
        Validator.notBlank(plainPassword, "password");

        try (Connection conn = Database.getConnection()) {
            User user = userDao.findByUsername(conn, username)
                    .orElseThrow(() -> new AuthenticationException("invalid username or password"));
            if (!user.isActive()) {
                throw new AuthenticationException("this account has been deactivated");
            }
            if (!PasswordHasher.matches(plainPassword, user.getPasswordHash())) {
                throw new AuthenticationException("invalid username or password");
            }
            return user;
        } catch (SQLException e) {
            throw new IllegalStateException("login failed", e);
        }
    }
}
