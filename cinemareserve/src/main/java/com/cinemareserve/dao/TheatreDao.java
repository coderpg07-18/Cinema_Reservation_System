package com.cinemareserve.dao;

import com.cinemareserve.domain.Theatre;
import com.cinemareserve.domain.VenueStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TheatreDao {

    public Theatre insert(Connection conn, Theatre t) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO theatres (name, location, status) VALUES (?, ?, ?)")) {
            ps.setString(1, t.getName());
            ps.setString(2, t.getLocation());
            ps.setString(3, t.getStatus().name());
            ps.executeUpdate();
            t.setId(com.cinemareserve.db.Database.lastInsertRowId(conn));
        }
        return t;
    }

    public Optional<Theatre> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM theatres WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Theatre> findAllActive(Connection conn) throws SQLException {
        List<Theatre> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM theatres WHERE status='ACTIVE' ORDER BY name")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    private Theatre map(ResultSet rs) throws SQLException {
        Theatre t = new Theatre();
        t.setId(rs.getLong("id"));
        t.setName(rs.getString("name"));
        t.setLocation(rs.getString("location"));
        t.setStatus(VenueStatus.valueOf(rs.getString("status")));
        return t;
    }
}
