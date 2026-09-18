package com.cinemareserve.dao;

import com.cinemareserve.db.Database;

import com.cinemareserve.domain.Reservation;
import com.cinemareserve.domain.ReservationStatus;
import com.cinemareserve.domain.ReservedSeat;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReservationDao {

    public Reservation insertReservation(Connection conn, Reservation r) throws SQLException {
        String sql = "INSERT INTO reservations (booking_reference, user_id, showtime_id, status, total_amount, hold_expires_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getBookingReference());
            ps.setLong(2, r.getUserId());
            ps.setLong(3, r.getShowtimeId());
            ps.setString(4, r.getStatus().name());
            ps.setDouble(5, r.getTotalAmount());
            ps.setString(6, r.getHoldExpiresAt() == null ? null : r.getHoldExpiresAt().toString());
            ps.executeUpdate();
            r.setId(Database.lastInsertRowId(conn));
        }
        return r;
    }

    /**
     * Inserts one row per seat into reserved_seats. This is where the double-booking
     * guarantee is physically enforced: uq_reserved_seat_active is a UNIQUE INDEX on
     * (showtime_id, seat_id), so if another transaction has already inserted a row for
     * the same seat on this showtime, SQLite raises SQLITE_CONSTRAINT and this call
     * throws SQLException -- the calling service maps that to SeatUnavailableException.
     */
    public void insertReservedSeats(Connection conn, List<ReservedSeat> seats) throws SQLException {
        String sql = "INSERT INTO reserved_seats (reservation_id, showtime_id, seat_id, price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ReservedSeat rs : seats) {
                ps.setLong(1, rs.getReservationId());
                ps.setLong(2, rs.getShowtimeId());
                ps.setLong(3, rs.getSeatId());
                ps.setDouble(4, rs.getPrice());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Seat ids currently blocked (HELD or CONFIRMED) for a showtime. */
    public List<Long> findBlockedSeatIds(Connection conn, long showtimeId) throws SQLException {
        String sql = "SELECT rs.seat_id FROM reserved_seats rs " +
                     "JOIN reservations r ON r.id = rs.reservation_id " +
                     "WHERE rs.showtime_id = ? AND r.status IN ('HELD', 'CONFIRMED')";
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong("seat_id"));
            }
        }
        return out;
    }

    public void updateStatus(Connection conn, long reservationId, ReservationStatus status,
                              LocalDateTime confirmedAt, LocalDateTime cancelledAt) throws SQLException {
        String sql = "UPDATE reservations SET status=?, confirmed_at=?, cancelled_at=? WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, confirmedAt == null ? null : confirmedAt.toString());
            ps.setString(3, cancelledAt == null ? null : cancelledAt.toString());
            ps.setLong(4, reservationId);
            ps.executeUpdate();
        }
    }

    /** Frees the seats by removing their reserved_seats rows -- used on cancel/expire. */
    public void deleteReservedSeats(Connection conn, long reservationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM reserved_seats WHERE reservation_id = ?")) {
            ps.setLong(1, reservationId);
            ps.executeUpdate();
        }
    }

    public Optional<Reservation> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM reservations WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Reservation r = map(rs);
                r.setSeats(findSeatsForReservation(conn, r.getId()));
                return Optional.of(r);
            }
        }
    }

    public List<Reservation> findByUser(Connection conn, long userId) throws SQLException {
        List<Reservation> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM reservations WHERE user_id = ? ORDER BY created_at DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation r = map(rs);
                    r.setSeats(findSeatsForReservation(conn, r.getId()));
                    out.add(r);
                }
            }
        }
        return out;
    }

    public List<ReservedSeat> findSeatsForReservation(Connection conn, long reservationId) throws SQLException {
        String sql = "SELECT rs.*, se.seat_row, se.seat_number FROM reserved_seats rs " +
                     "JOIN seats se ON se.id = rs.seat_id WHERE rs.reservation_id = ?";
        List<ReservedSeat> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReservedSeat seat = new ReservedSeat();
                    seat.setId(rs.getLong("id"));
                    seat.setReservationId(rs.getLong("reservation_id"));
                    seat.setShowtimeId(rs.getLong("showtime_id"));
                    seat.setSeatId(rs.getLong("seat_id"));
                    seat.setSeatLabel(rs.getString("seat_row") + rs.getInt("seat_number"));
                    seat.setPrice(rs.getDouble("price"));
                    out.add(seat);
                }
            }
        }
        return out;
    }

    /** Every HELD reservation whose hold has expired -- used by the hold-expiry sweep. */
    public List<Reservation> findExpiredHolds(Connection conn, LocalDateTime now) throws SQLException {
        String sql = "SELECT * FROM reservations WHERE status = 'HELD' AND hold_expires_at IS NOT NULL AND hold_expires_at < ?";
        List<Reservation> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /** Maps seatId -> reservation status ("HELD" or "CONFIRMED") for every currently blocked seat. */
    public java.util.Map<Long, String> findSeatStatusMap(Connection conn, long showtimeId) throws SQLException {
        String sql = "SELECT rs.seat_id, r.status FROM reserved_seats rs " +
                     "JOIN reservations r ON r.id = rs.reservation_id " +
                     "WHERE rs.showtime_id = ? AND r.status IN ('HELD', 'CONFIRMED')";
        java.util.Map<Long, String> out = new java.util.HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getLong("seat_id"), rs.getString("status"));
            }
        }
        return out;
    }

    public List<Reservation> findByShowtime(Connection conn, long showtimeId) throws SQLException {
        List<Reservation> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM reservations WHERE showtime_id = ?")) {
            ps.setLong(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    private Reservation map(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setId(rs.getLong("id"));
        r.setBookingReference(rs.getString("booking_reference"));
        r.setUserId(rs.getLong("user_id"));
        r.setShowtimeId(rs.getLong("showtime_id"));
        r.setStatus(ReservationStatus.valueOf(rs.getString("status")));
        r.setTotalAmount(rs.getDouble("total_amount"));
        r.setCreatedAt(LocalDateTime.parse(rs.getString("created_at").replace(' ', 'T')));
        String confirmedAt = rs.getString("confirmed_at");
        r.setConfirmedAt(confirmedAt == null ? null : LocalDateTime.parse(confirmedAt));
        String cancelledAt = rs.getString("cancelled_at");
        r.setCancelledAt(cancelledAt == null ? null : LocalDateTime.parse(cancelledAt));
        String holdExpires = rs.getString("hold_expires_at");
        r.setHoldExpiresAt(holdExpires == null ? null : LocalDateTime.parse(holdExpires));
        return r;
    }
}
