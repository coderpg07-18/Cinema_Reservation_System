package com.cinemareserve.dao;

import com.cinemareserve.db.Database;

import com.cinemareserve.domain.Genre;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GenreDao {

    public Genre insert(Connection conn, Genre g) throws SQLException {
        String sql = "INSERT INTO genres (name) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, g.getName());
            ps.executeUpdate();
            g.setId(Database.lastInsertRowId(conn));
        }
        return g;
    }

    public Optional<Genre> findByName(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM genres WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Genre> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM genres WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Genre> findAll(Connection conn) throws SQLException {
        List<Genre> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM genres ORDER BY name")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    private Genre map(ResultSet rs) throws SQLException {
        return new Genre(rs.getLong("id"), rs.getString("name"));
    }
}
