package com.cinemareserve.dao;

import com.cinemareserve.domain.Seat;
import com.cinemareserve.domain.SeatType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SeatDao {

    public Seat insert(Connection conn, Seat seat) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO seats (screen_id, seat_row, seat_number, seat_type) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, seat.getScreenId());
            ps.setString(2, seat.getSeatRow());
            ps.setInt(3, seat.getSeatNumber());
            ps.setString(4, seat.getSeatType().name());
            ps.executeUpdate();
            seat.setId(com.cinemareserve.db.Database.lastInsertRowId(conn));
        }
        return seat;
    }

    public List<Seat> findByScreen(Connection conn, long screenId) throws SQLException {
        List<Seat> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM seats WHERE screen_id = ? ORDER BY seat_row, seat_number")) {
            ps.setLong(1, screenId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public Optional<Seat> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM seats WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private Seat map(ResultSet rs) throws SQLException {
        Seat s = new Seat();
        s.setId(rs.getLong("id"));
        s.setScreenId(rs.getLong("screen_id"));
        s.setSeatRow(rs.getString("seat_row"));
        s.setSeatNumber(rs.getInt("seat_number"));
        s.setSeatType(SeatType.valueOf(rs.getString("seat_type")));
        return s;
    }
}
