package com.cinemareserve.dao;

import com.cinemareserve.domain.Screen;
import com.cinemareserve.domain.VenueStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScreenDao {

    public Screen insert(Connection conn, Screen s) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO screens (theatre_id, screen_name, capacity, status) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, s.getTheatreId());
            ps.setString(2, s.getScreenName());
            ps.setInt(3, s.getCapacity());
            ps.setString(4, s.getStatus().name());
            ps.executeUpdate();
            s.setId(com.cinemareserve.db.Database.lastInsertRowId(conn));
        }
        return s;
    }

    public Optional<Screen> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM screens WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Screen> findByTheatre(Connection conn, long theatreId) throws SQLException {
        List<Screen> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM screens WHERE theatre_id = ? AND status='ACTIVE' ORDER BY screen_name")) {
            ps.setLong(1, theatreId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    private Screen map(ResultSet rs) throws SQLException {
        Screen s = new Screen();
        s.setId(rs.getLong("id"));
        s.setTheatreId(rs.getLong("theatre_id"));
        s.setScreenName(rs.getString("screen_name"));
        s.setCapacity(rs.getInt("capacity"));
        s.setStatus(VenueStatus.valueOf(rs.getString("status")));
        return s;
    }
}
