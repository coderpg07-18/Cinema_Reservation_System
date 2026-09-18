package com.cinemareserve.dao;

import com.cinemareserve.db.Database;

import com.cinemareserve.domain.Showtime;
import com.cinemareserve.domain.ShowtimeStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ShowtimeDao {

    private static final String SELECT_JOIN =
        "SELECT s.*, m.title AS movie_title, sc.screen_name AS screen_name " +
        "FROM showtimes s JOIN movies m ON m.id = s.movie_id JOIN screens sc ON sc.id = s.screen_id ";

    public Showtime insert(Connection conn, Showtime st) throws SQLException {
        String sql = "INSERT INTO showtimes (movie_id, screen_id, start_time, end_time, base_price, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, st.getMovieId());
            ps.setLong(2, st.getScreenId());
            ps.setString(3, st.getStartTime().toString());
            ps.setString(4, st.getEndTime().toString());
            ps.setDouble(5, st.getBasePrice());
            ps.setString(6, st.getStatus().name());
            ps.executeUpdate();
            st.setId(Database.lastInsertRowId(conn));
        }
        return st;
    }

    public Optional<Showtime> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + " WHERE s.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Two intervals [startA, endA) and [startB, endB) overlap iff startA < endB AND startB < endA.
     * This single inequality-based check is the whole overlap algorithm; no special-casing of
     * "starts during", "ends during", "fully contains" is needed because all three cases satisfy it.
     */
    public List<Showtime> findOverlapping(Connection conn, long screenId, LocalDateTime start, LocalDateTime end, Long excludeShowtimeId) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_JOIN +
            " WHERE s.screen_id = ? AND s.status = 'SCHEDULED' AND s.start_time < ? AND ? < s.end_time");
        if (excludeShowtimeId != null) {
            sql.append(" AND s.id != ?");
        }
        List<Showtime> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, screenId);
            ps.setString(2, end.toString());
            ps.setString(3, start.toString());
            if (excludeShowtimeId != null) {
                ps.setLong(4, excludeShowtimeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<Showtime> findByMovieAndDate(Connection conn, long movieId, java.time.LocalDate date) throws SQLException {
        String sql = SELECT_JOIN + " WHERE s.movie_id = ? AND s.status='SCHEDULED' AND date(s.start_time) = ? ORDER BY s.start_time";
        List<Showtime> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, movieId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<Showtime> findAllUpcoming(Connection conn) throws SQLException {
        String sql = SELECT_JOIN + " WHERE s.status='SCHEDULED' ORDER BY s.start_time";
        List<Showtime> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    private Showtime map(ResultSet rs) throws SQLException {
        Showtime s = new Showtime();
        s.setId(rs.getLong("id"));
        s.setMovieId(rs.getLong("movie_id"));
        s.setMovieTitle(rs.getString("movie_title"));
        s.setScreenId(rs.getLong("screen_id"));
        s.setScreenName(rs.getString("screen_name"));
        s.setStartTime(LocalDateTime.parse(rs.getString("start_time")));
        s.setEndTime(LocalDateTime.parse(rs.getString("end_time")));
        s.setBasePrice(rs.getDouble("base_price"));
        s.setStatus(ShowtimeStatus.valueOf(rs.getString("status")));
        return s;
    }
}
