package com.cinemareserve.service;

import com.cinemareserve.db.Database;
import com.cinemareserve.domain.User;
import com.cinemareserve.exception.AuthorizationException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * All figures here are computed with SQL aggregation (COUNT/SUM/GROUP BY) run against
 * the database, not by loading every row into Java and summing in a loop -- the brief
 * specifically calls this out, and it matters: these queries scale with an index, a
 * naive load-everything approach does not.
 */
public class ReportingService {

    public record SummaryReport(long totalReservations, long confirmedReservations, long cancelledReservations,
                                 long totalTicketsSold, double totalRevenue) {}

    public record MovieRevenueRow(String title, long ticketsSold, double revenue) {}

    public record ScreenUtilizationRow(String theatreName, String screenName, long totalSeats,
                                        long ticketsSold, double occupancyPercent) {}

    public SummaryReport summary(User actor) {
        requireAdmin(actor);
        String sql = """
            SELECT
              COUNT(*) AS total_reservations,
              SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed,
              SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled,
              COALESCE((SELECT COUNT(*) FROM reserved_seats rs JOIN reservations r2 ON r2.id = rs.reservation_id WHERE r2.status = 'CONFIRMED'), 0) AS tickets,
              COALESCE((SELECT SUM(total_amount) FROM reservations WHERE status = 'CONFIRMED'), 0) AS revenue
            FROM reservations
            """;
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return new SummaryReport(
                rs.getLong("total_reservations"),
                rs.getLong("confirmed"),
                rs.getLong("cancelled"),
                rs.getLong("tickets"),
                rs.getDouble("revenue"));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to build summary report", e);
        }
    }

    public List<MovieRevenueRow> revenueByMovie(User actor) {
        requireAdmin(actor);
        String sql = """
            SELECT m.title AS title, COUNT(rs.id) AS tickets, COALESCE(SUM(rs.price), 0) AS revenue
            FROM movies m
            JOIN showtimes s ON s.movie_id = m.id
            JOIN reserved_seats rs ON rs.showtime_id = s.id
            JOIN reservations r ON r.id = rs.reservation_id AND r.status = 'CONFIRMED'
            GROUP BY m.id, m.title
            ORDER BY revenue DESC
            """;
        List<MovieRevenueRow> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new MovieRevenueRow(rs.getString("title"), rs.getLong("tickets"), rs.getDouble("revenue")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to build movie revenue report", e);
        }
        return out;
    }

    public List<ScreenUtilizationRow> screenUtilization(User actor) {
        requireAdmin(actor);
        String sql = """
            SELECT t.name AS theatre_name, sc.screen_name AS screen_name,
                   (SELECT COUNT(*) FROM seats WHERE screen_id = sc.id) AS total_seats,
                   COUNT(rs.id) AS tickets
            FROM screens sc
            JOIN theatres t ON t.id = sc.theatre_id
            LEFT JOIN showtimes sh ON sh.screen_id = sc.id
            LEFT JOIN reserved_seats rs ON rs.showtime_id = sh.id
            LEFT JOIN reservations r ON r.id = rs.reservation_id AND r.status = 'CONFIRMED'
            GROUP BY sc.id, t.name, sc.screen_name
            ORDER BY t.name, sc.screen_name
            """;
        List<ScreenUtilizationRow> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                long totalSeats = rs.getLong("total_seats");
                long tickets = rs.getLong("tickets");
                double occupancy = totalSeats == 0 ? 0 : (tickets * 100.0) / totalSeats;
                out.add(new ScreenUtilizationRow(rs.getString("theatre_name"), rs.getString("screen_name"),
                        totalSeats, tickets, occupancy));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to build screen utilization report", e);
        }
        return out;
    }

    private void requireAdmin(User actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new AuthorizationException("admin privileges required for this operation");
        }
    }
}
