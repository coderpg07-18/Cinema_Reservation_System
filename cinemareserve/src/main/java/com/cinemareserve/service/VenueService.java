package com.cinemareserve.service;

import com.cinemareserve.dao.ScreenDao;
import com.cinemareserve.dao.SeatDao;
import com.cinemareserve.dao.TheatreDao;
import com.cinemareserve.db.Database;
import com.cinemareserve.domain.*;
import com.cinemareserve.exception.NotFoundException;
import com.cinemareserve.util.Validator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class VenueService {

    private final TheatreDao theatreDao;
    private final ScreenDao screenDao;
    private final SeatDao seatDao;

    public VenueService(TheatreDao theatreDao, ScreenDao screenDao, SeatDao seatDao) {
        this.theatreDao = theatreDao;
        this.screenDao = screenDao;
        this.seatDao = seatDao;
    }

    public Theatre createTheatre(User actor, String name, String location) {
        AuthorizationGuard.requireAdmin(actor);
        Validator.notBlank(name, "theatre name");
        Validator.notBlank(location, "location");
        Theatre t = new Theatre();
        t.setName(name);
        t.setLocation(location);
        t.setStatus(VenueStatus.ACTIVE);
        try (Connection conn = Database.getConnection()) {
            return theatreDao.insert(conn, t);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create theatre", e);
        }
    }

    public Screen createScreen(User actor, long theatreId, String screenName, int capacity) {
        AuthorizationGuard.requireAdmin(actor);
        Validator.notBlank(screenName, "screen name");
        Validator.positive(capacity, "capacity");
        try (Connection conn = Database.getConnection()) {
            theatreDao.findById(conn, theatreId)
                    .orElseThrow(() -> new NotFoundException("theatre not found: " + theatreId));
            Screen s = new Screen();
            s.setTheatreId(theatreId);
            s.setScreenName(screenName);
            s.setCapacity(capacity);
            s.setStatus(VenueStatus.ACTIVE);
            return screenDao.insert(conn, s);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create screen", e);
        }
    }

    /**
     * Builds a standard rectangular seat grid, e.g. rows A..E x 1..10, all STANDARD by
     * default. Admins can still add individual PREMIUM/RECLINER seats via createSeat.
     */
    public List<Seat> generateSeatGrid(User actor, long screenId, int rows, int seatsPerRow) {
        AuthorizationGuard.requireAdmin(actor);
        Validator.positive(rows, "rows");
        Validator.positive(seatsPerRow, "seatsPerRow");
        try (Connection conn = Database.getConnection()) {
            screenDao.findById(conn, screenId)
                    .orElseThrow(() -> new NotFoundException("screen not found: " + screenId));
            for (int r = 0; r < rows; r++) {
                String rowLabel = String.valueOf((char) ('A' + r));
                for (int n = 1; n <= seatsPerRow; n++) {
                    Seat seat = new Seat();
                    seat.setScreenId(screenId);
                    seat.setSeatRow(rowLabel);
                    seat.setSeatNumber(n);
                    seat.setSeatType(SeatType.STANDARD);
                    seatDao.insert(conn, seat);
                }
            }
            return seatDao.findByScreen(conn, screenId);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to generate seat grid", e);
        }
    }

    public Seat createSeat(User actor, long screenId, String row, int number, SeatType type) {
        AuthorizationGuard.requireAdmin(actor);
        Validator.notBlank(row, "seat row");
        Validator.positive(number, "seat number");
        try (Connection conn = Database.getConnection()) {
            screenDao.findById(conn, screenId)
                    .orElseThrow(() -> new NotFoundException("screen not found: " + screenId));
            Seat seat = new Seat();
            seat.setScreenId(screenId);
            seat.setSeatRow(row.toUpperCase());
            seat.setSeatNumber(number);
            seat.setSeatType(type);
            return seatDao.insert(conn, seat);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create seat", e);
        }
    }

    public List<Theatre> listTheatres() {
        try (Connection conn = Database.getConnection()) {
            return theatreDao.findAllActive(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list theatres", e);
        }
    }

    public List<Screen> listScreens(long theatreId) {
        try (Connection conn = Database.getConnection()) {
            return screenDao.findByTheatre(conn, theatreId);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list screens", e);
        }
    }

    public List<Seat> listSeats(long screenId) {
        try (Connection conn = Database.getConnection()) {
            return seatDao.findByScreen(conn, screenId);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list seats", e);
        }
    }

}
