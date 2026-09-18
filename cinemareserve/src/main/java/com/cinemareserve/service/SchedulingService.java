package com.cinemareserve.service;

import com.cinemareserve.dao.MovieDao;
import com.cinemareserve.dao.ScreenDao;
import com.cinemareserve.dao.ShowtimeDao;
import com.cinemareserve.db.Database;
import com.cinemareserve.domain.*;
import com.cinemareserve.exception.NotFoundException;
import com.cinemareserve.exception.ScheduleConflictException;
import com.cinemareserve.exception.ValidationException;
import com.cinemareserve.util.Validator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SchedulingService {

    private final ShowtimeDao showtimeDao;
    private final MovieDao movieDao;
    private final ScreenDao screenDao;

    public SchedulingService(ShowtimeDao showtimeDao, MovieDao movieDao, ScreenDao screenDao) {
        this.showtimeDao = showtimeDao;
        this.movieDao = movieDao;
        this.screenDao = screenDao;
    }

    /**
     * Creates a showtime after checking, in order: the movie exists and is active, the
     * screen exists and is active, the time window is valid, and -- the important part --
     * no SCHEDULED showtime already occupies any part of that window on that screen.
     *
     * The overlap check and the insert happen inside a single transaction so a second
     * admin creating a conflicting showtime concurrently cannot slip in between the
     * check and the write; SQLite's file-level write lock serializes the two calls.
     */
    public Showtime createShowtime(User actor, long movieId, long screenId, LocalDateTime start, LocalDateTime end, double basePrice) {
        if (actor == null || !actor.isAdmin()) {
            throw new com.cinemareserve.exception.AuthorizationException("admin privileges required for this operation");
        }
        if (!start.isBefore(end)) {
            throw new ValidationException("start time must be before end time");
        }
        Validator.notNegative(basePrice, "base price");

        try (Connection conn = Database.getConnection()) {
            Database.beginWriteTransaction(conn);
            try {
                Movie movie = movieDao.findById(conn, movieId)
                        .orElseThrow(() -> new NotFoundException("movie not found: " + movieId));
                if (movie.getStatus() != MovieStatus.ACTIVE) {
                    throw new ValidationException("cannot schedule an inactive movie");
                }
                Screen screen = screenDao.findById(conn, screenId)
                        .orElseThrow(() -> new NotFoundException("screen not found: " + screenId));
                if (screen.getStatus() != VenueStatus.ACTIVE) {
                    throw new ValidationException("cannot schedule a show on an inactive screen");
                }

                List<Showtime> overlaps = showtimeDao.findOverlapping(conn, screenId, start, end, null);
                if (!overlaps.isEmpty()) {
                    Showtime clash = overlaps.get(0);
                    throw new ScheduleConflictException(String.format(
                        "screen %s already has \"%s\" scheduled %s - %s, which overlaps the requested slot",
                        screen.getScreenName(), clash.getMovieTitle(), clash.getStartTime(), clash.getEndTime()));
                }

                Showtime showtime = new Showtime();
                showtime.setMovieId(movieId);
                showtime.setScreenId(screenId);
                showtime.setStartTime(start);
                showtime.setEndTime(end);
                showtime.setBasePrice(basePrice);
                showtime.setStatus(ShowtimeStatus.SCHEDULED);
                showtimeDao.insert(conn, showtime);

                conn.commit();
                return showtime;
            } catch (RuntimeException | SQLException e) {
                conn.rollback();
                if (e instanceof RuntimeException re) throw re;
                throw new IllegalStateException("failed to create showtime", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create showtime", e);
        }
    }

    public Showtime getShowtime(long id) {
        try (Connection conn = Database.getConnection()) {
            return showtimeDao.findById(conn, id)
                    .orElseThrow(() -> new NotFoundException("showtime not found: " + id));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to fetch showtime", e);
        }
    }

    public List<Showtime> findByMovieAndDate(long movieId, LocalDate date) {
        try (Connection conn = Database.getConnection()) {
            return showtimeDao.findByMovieAndDate(conn, movieId, date);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to search showtimes", e);
        }
    }

    public List<Showtime> listUpcoming() {
        try (Connection conn = Database.getConnection()) {
            return showtimeDao.findAllUpcoming(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list showtimes", e);
        }
    }
}
