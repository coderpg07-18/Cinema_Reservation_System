package com.cinemareserve.service;

import com.cinemareserve.dao.ReservationDao;
import com.cinemareserve.dao.SeatDao;
import com.cinemareserve.dao.ShowtimeDao;
import com.cinemareserve.db.Database;
import com.cinemareserve.domain.*;
import com.cinemareserve.exception.*;
import com.cinemareserve.util.BookingReferenceGenerator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The reservation engine. This is the part of the system where correctness under
 * concurrency matters more than anywhere else -- see the class-level notes on
 * {@link #holdSeats} for how double booking is prevented.
 */
public class ReservationService {

    /** How long a seat hold survives before it's swept back to AVAILABLE. Kept as a
     *  single named constant rather than scattered magic numbers, and easy to move to
     *  an external config file later without touching business logic. */
    public static final int HOLD_DURATION_MINUTES = 10;

    /** A confirmed reservation can only be cancelled up to this many minutes before the show. */
    public static final int CANCELLATION_WINDOW_MINUTES = 60;

    /** PREMIUM/RECLINER seats cost more than the showtime's base price. */
    private static final double PREMIUM_SURCHARGE = 50.0;
    private static final double RECLINER_SURCHARGE = 100.0;

    private final ReservationDao reservationDao;
    private final ShowtimeDao showtimeDao;
    private final SeatDao seatDao;

    public ReservationService(ReservationDao reservationDao, ShowtimeDao showtimeDao, SeatDao seatDao) {
        this.reservationDao = reservationDao;
        this.showtimeDao = showtimeDao;
        this.seatDao = seatDao;
    }

    public enum SeatAvailability { AVAILABLE, HELD, BOOKED }

    public record SeatAvailabilityView(Seat seat, SeatAvailability availability) {}

    /** Full seat map for a showtime: every seat on that screen, tagged AVAILABLE / HELD / BOOKED. */
    public List<SeatAvailabilityView> getSeatMap(long showtimeId) {
        try (Connection conn = Database.getConnection()) {
            expireStaleHolds(conn);
            Showtime showtime = showtimeDao.findById(conn, showtimeId)
                    .orElseThrow(() -> new NotFoundException("showtime not found: " + showtimeId));
            List<Seat> seats = seatDao.findByScreen(conn, showtime.getScreenId());
            Map<Long, String> statusMap = reservationDao.findSeatStatusMap(conn, showtimeId);
            List<SeatAvailabilityView> out = new ArrayList<>();
            for (Seat seat : seats) {
                String status = statusMap.get(seat.getId());
                SeatAvailability availability = switch (status) {
                    case "CONFIRMED" -> SeatAvailability.BOOKED;
                    case "HELD" -> SeatAvailability.HELD;
                    case null -> SeatAvailability.AVAILABLE;
                    default -> SeatAvailability.AVAILABLE;
                };
                out.add(new SeatAvailabilityView(seat, availability));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("failed to load seat map", e);
        }
    }

    /**
     * Places a temporary hold on the requested seats for the showtime.
     *
     * THE RACE CONDITION: two users can both call holdSeats() for seat A2 on the same
     * showtime within the same millisecond. A naive implementation would (1) SELECT to
     * check A2 is free, see it is, then (2) INSERT the booking -- and if both requests
     * run their SELECT before either runs its INSERT, both conclude the seat is free
     * and both insert, producing two bookings for one seat.
     *
     * THE FIX: the "check" and the "write" are not actually two separate steps here --
     * the write itself is the check. uq_reserved_seat_active is a UNIQUE INDEX on
     * (showtime_id, seat_id) in the schema, so the INSERT into reserved_seats is an
     * atomic compare-and-insert at the database level: whichever transaction's INSERT
     * reaches SQLite's single writer lock first succeeds, and the second one fails with
     * SQLITE_CONSTRAINT_UNIQUE before it can ever produce a duplicate row. There is no
     * gap between "check" and "write" for a second thread to land in, because SQLite
     * only allows one writer at a time and the constraint is checked as part of that
     * same write. We still do an upfront availability read for a fast, friendly error
     * message, but it is not what guarantees correctness -- the constraint is.
     */
    public Reservation holdSeats(User actor, long showtimeId, List<Long> seatIds) {
        if (actor == null) {
            throw new AuthorizationException("authentication required");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new ValidationException("at least one seat must be selected");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new ValidationException("duplicate seat selected");
        }

        try (Connection conn = Database.getConnection()) {
            Database.beginWriteTransaction(conn);
            try {
                expireStaleHolds(conn);

                Showtime showtime = showtimeDao.findById(conn, showtimeId)
                        .orElseThrow(() -> new NotFoundException("showtime not found: " + showtimeId));
                if (showtime.getStatus() != ShowtimeStatus.SCHEDULED) {
                    throw new ValidationException("this showtime has been cancelled");
                }
                if (!showtime.getStartTime().isAfter(LocalDateTime.now())) {
                    throw new ValidationException("cannot book a showtime that has already started");
                }

                Map<Long, Seat> seatsById = new HashMap<>();
                for (Long seatId : seatIds) {
                    Seat seat = seatDao.findById(conn, seatId)
                            .orElseThrow(() -> new NotFoundException("seat not found: " + seatId));
                    if (!seat.getScreenId().equals(showtime.getScreenId())) {
                        throw new ValidationException("seat " + seat.getLabel() + " does not belong to this showtime's screen");
                    }
                    seatsById.put(seatId, seat);
                }

                // Friendly, fast pre-check (not the source of correctness -- see method javadoc).
                Set<Long> blocked = new HashSet<>(reservationDao.findBlockedSeatIds(conn, showtimeId));
                List<String> alreadyTaken = seatIds.stream()
                        .filter(blocked::contains)
                        .map(id -> seatsById.get(id).getLabel())
                        .toList();
                if (!alreadyTaken.isEmpty()) {
                    throw new SeatUnavailableException("seat(s) already held or booked: " + String.join(", ", alreadyTaken));
                }

                Reservation reservation = new Reservation();
                reservation.setBookingReference(BookingReferenceGenerator.generate());
                reservation.setUserId(actor.getId());
                reservation.setShowtimeId(showtimeId);
                reservation.setStatus(ReservationStatus.HELD);
                reservation.setHoldExpiresAt(LocalDateTime.now().plusMinutes(HOLD_DURATION_MINUTES));

                double total = 0;
                List<ReservedSeat> reservedSeats = new ArrayList<>();
                for (Long seatId : seatIds) {
                    Seat seat = seatsById.get(seatId);
                    double price = priceFor(showtime, seat);
                    total += price;
                    ReservedSeat rs = new ReservedSeat();
                    rs.setShowtimeId(showtimeId);
                    rs.setSeatId(seatId);
                    rs.setPrice(price);
                    reservedSeats.add(rs);
                }
                reservation.setTotalAmount(total);
                reservationDao.insertReservation(conn, reservation);
                for (ReservedSeat rs : reservedSeats) {
                    rs.setReservationId(reservation.getId());
                }

                try {
                    reservationDao.insertReservedSeats(conn, reservedSeats);
                } catch (SQLException e) {
                    // This is the real guarantee: the unique index rejected a seat that
                    // became taken between our pre-check and this insert.
                    if (isUniqueConstraintViolation(e)) {
                        throw new SeatUnavailableException(
                            "one or more selected seats were just taken by another booking -- please reselect");
                    }
                    throw e;
                }

                conn.commit();
                reservation.setSeats(reservationDao.findSeatsForReservation(conn, reservation.getId()));
                return reservation;
            } catch (RuntimeException re) {
                conn.rollback();
                throw re;
            } catch (SQLException e) {
                conn.rollback();
                throw new IllegalStateException("failed to hold seats", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to hold seats", e);
        }
    }

    public Reservation confirmReservation(User actor, long reservationId) {
        if (actor == null) {
            throw new AuthorizationException("authentication required");
        }
        try (Connection conn = Database.getConnection()) {
            Database.beginWriteTransaction(conn);
            try {
                expireStaleHolds(conn);

                Reservation reservation = reservationDao.findById(conn, reservationId)
                        .orElseThrow(() -> new NotFoundException("reservation not found: " + reservationId));
                if (!reservation.getUserId().equals(actor.getId()) && !actor.isAdmin()) {
                    throw new AuthorizationException("this reservation does not belong to you");
                }
                if (reservation.getStatus() != ReservationStatus.HELD) {
                    throw new InvalidReservationStateException(
                        "reservation is " + reservation.getStatus() + ", only a HELD reservation can be confirmed");
                }
                LocalDateTime now = LocalDateTime.now();
                reservationDao.updateStatus(conn, reservationId, ReservationStatus.CONFIRMED, now, null);
                conn.commit();

                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservation.setConfirmedAt(now);
                return reservation;
            } catch (RuntimeException re) {
                conn.rollback();
                throw re;
            } catch (SQLException e) {
                conn.rollback();
                throw new IllegalStateException("failed to confirm reservation", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to confirm reservation", e);
        }
    }

    /**
     * Cancellation policy: a HELD reservation may be cancelled any time before the show
     * starts (it hasn't been paid for). A CONFIRMED reservation may only be cancelled up
     * to CANCELLATION_WINDOW_MINUTES before the showtime starts. Past shows, already
     * cancelled/expired reservations, and reservations belonging to another user are all
     * rejected with a specific exception rather than a generic failure.
     */
    public Reservation cancelReservation(User actor, long reservationId) {
        if (actor == null) {
            throw new AuthorizationException("authentication required");
        }
        try (Connection conn = Database.getConnection()) {
            Database.beginWriteTransaction(conn);
            try {
                Reservation reservation = reservationDao.findById(conn, reservationId)
                        .orElseThrow(() -> new NotFoundException("reservation not found: " + reservationId));
                if (!reservation.getUserId().equals(actor.getId()) && !actor.isAdmin()) {
                    throw new AuthorizationException("you can only cancel your own reservations");
                }
                if (reservation.getStatus() == ReservationStatus.CANCELLED) {
                    throw new InvalidReservationStateException("reservation is already cancelled");
                }
                if (reservation.getStatus() == ReservationStatus.EXPIRED) {
                    throw new InvalidReservationStateException("this hold has already expired");
                }

                Showtime showtime = showtimeDao.findById(conn, reservation.getShowtimeId())
                        .orElseThrow(() -> new NotFoundException("showtime not found"));
                LocalDateTime now = LocalDateTime.now();
                if (!showtime.getStartTime().isAfter(now)) {
                    throw new InvalidReservationStateException("cannot cancel a reservation for a show that has already started");
                }
                if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                    long minutesUntilShow = java.time.Duration.between(now, showtime.getStartTime()).toMinutes();
                    if (minutesUntilShow < CANCELLATION_WINDOW_MINUTES) {
                        throw new InvalidReservationStateException(
                            "confirmed reservations can only be cancelled at least " + CANCELLATION_WINDOW_MINUTES +
                            " minutes before the show; this show starts in " + minutesUntilShow + " minute(s)");
                    }
                }

                reservationDao.deleteReservedSeats(conn, reservationId);
                reservationDao.updateStatus(conn, reservationId, ReservationStatus.CANCELLED, reservation.getConfirmedAt(), now);
                conn.commit();

                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setCancelledAt(now);
                reservation.getSeats().clear();
                return reservation;
            } catch (RuntimeException re) {
                conn.rollback();
                throw re;
            } catch (SQLException e) {
                conn.rollback();
                throw new IllegalStateException("failed to cancel reservation", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to cancel reservation", e);
        }
    }

    public List<Reservation> listForUser(User actor) {
        try (Connection conn = Database.getConnection()) {
            return reservationDao.findByUser(conn, actor.getId());
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list reservations", e);
        }
    }

    /** Sweeps HELD reservations whose hold has passed, freeing their seats. Called at the
     *  start of every reservation-touching operation so state is always consistent before
     *  we act on it, without needing a separate background thread for correctness. */
    private void expireStaleHolds(Connection conn) throws SQLException {
        List<Reservation> expired = reservationDao.findExpiredHolds(conn, LocalDateTime.now());
        for (Reservation r : expired) {
            reservationDao.deleteReservedSeats(conn, r.getId());
            reservationDao.updateStatus(conn, r.getId(), ReservationStatus.EXPIRED, null, null);
        }
    }

    private double priceFor(Showtime showtime, Seat seat) {
        return switch (seat.getSeatType()) {
            case STANDARD -> showtime.getBasePrice();
            case PREMIUM -> showtime.getBasePrice() + PREMIUM_SURCHARGE;
            case RECLINER -> showtime.getBasePrice() + RECLINER_SURCHARGE;
        };
    }

    private boolean isUniqueConstraintViolation(SQLException e) {
        String msg = e.getMessage();
        return msg != null && msg.toUpperCase().contains("UNIQUE");
    }
}
