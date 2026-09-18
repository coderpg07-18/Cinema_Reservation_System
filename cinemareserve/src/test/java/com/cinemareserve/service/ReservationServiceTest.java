package com.cinemareserve.service;

import com.cinemareserve.TestDb;
import com.cinemareserve.TestFixtures;
import com.cinemareserve.domain.Reservation;
import com.cinemareserve.domain.User;
import com.cinemareserve.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    private TestFixtures fx;

    @BeforeEach
    void setUp() {
        TestDb.freshDatabase();
        fx = new TestFixtures();
        fx.createShowtime(48, 200.0); // 2 days out, well outside any cancellation window
    }

    @Test
    void availableSeatCanBeHeld() {
        long seatId = fx.seats.get(0).getId();
        Reservation r = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        assertEquals(com.cinemareserve.domain.ReservationStatus.HELD, r.getStatus());
        assertEquals(1, r.getSeats().size());
    }

    @Test
    void alreadyHeldSeatCannotBeHeldAgainByAnotherUser() {
        long seatId = fx.seats.get(0).getId();
        fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));

        User secondUser = fx.authService.register("seconduser", "second@example.com", "SecondPass1");
        assertThrows(SeatUnavailableException.class, () ->
            fx.reservationService.holdSeats(secondUser, fx.showtime.getId(), List.of(seatId)));
    }

    @Test
    void multipleDistinctSeatsCanBeHeldTogether() {
        long seat1 = fx.seats.get(0).getId();
        long seat2 = fx.seats.get(1).getId();
        Reservation r = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seat1, seat2));
        assertEquals(2, r.getSeats().size());
    }

    @Test
    void duplicateSeatInSameRequestIsRejected() {
        long seatId = fx.seats.get(0).getId();
        assertThrows(ValidationException.class, () ->
            fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId, seatId)));
    }

    @Test
    void unknownSeatIsRejected() {
        assertThrows(NotFoundException.class, () ->
            fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(999999L)));
    }

    @Test
    void confirmingHeldReservationSucceeds() {
        long seatId = fx.seats.get(0).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        Reservation confirmed = fx.reservationService.confirmReservation(fx.regularUser, held.getId());
        assertEquals(com.cinemareserve.domain.ReservationStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    void cannotConfirmAnotherUsersReservation() {
        long seatId = fx.seats.get(0).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        User otherUser = fx.authService.register("intruder", "intruder@example.com", "IntruderPass1");
        assertThrows(AuthorizationException.class, () ->
            fx.reservationService.confirmReservation(otherUser, held.getId()));
    }

    @Test
    void cannotConfirmAlreadyConfirmedReservation() {
        long seatId = fx.seats.get(0).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        fx.reservationService.confirmReservation(fx.regularUser, held.getId());
        assertThrows(InvalidReservationStateException.class, () ->
            fx.reservationService.confirmReservation(fx.regularUser, held.getId()));
    }

    @Test
    void upcomingReservationCanBeCancelled() {
        long seatId = fx.seats.get(0).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        fx.reservationService.confirmReservation(fx.regularUser, held.getId());
        Reservation cancelled = fx.reservationService.cancelReservation(fx.regularUser, held.getId());
        assertEquals(com.cinemareserve.domain.ReservationStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void cancellingFreesTheSeatForAnotherUser() {
        long seatId = fx.seats.get(0).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        fx.reservationService.cancelReservation(fx.regularUser, held.getId());

        User anotherUser = fx.authService.register("newbooker", "newbooker@example.com", "NewbookerPass1");
        Reservation r = fx.reservationService.holdSeats(anotherUser, fx.showtime.getId(), List.of(seatId));
        assertEquals(com.cinemareserve.domain.ReservationStatus.HELD, r.getStatus());
    }

    @Test
    void alreadyCancelledReservationCannotBeCancelledAgain() {
        long seatId = fx.seats.get(0).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        fx.reservationService.cancelReservation(fx.regularUser, held.getId());
        assertThrows(InvalidReservationStateException.class, () ->
            fx.reservationService.cancelReservation(fx.regularUser, held.getId()));
    }

    @Test
    void anotherUsersReservationCannotBeCancelled() {
        long seatId = fx.seats.get(0).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(), List.of(seatId));
        User intruder = fx.authService.register("intruder2", "intruder2@example.com", "IntruderPass1");
        assertThrows(AuthorizationException.class, () ->
            fx.reservationService.cancelReservation(intruder, held.getId()));
    }

    @Test
    void confirmedReservationCannotBeCancelledTooCloseToShowtime() {
        // Build a showtime just 30 minutes out -- inside the 60-minute cancellation window,
        // but still safely after "now" so holdSeats accepts it.
        var start = java.time.LocalDateTime.now().plusMinutes(30);
        var nearShowtime = fx.schedulingService.createShowtime(
            fx.admin, fx.movie.getId(), fx.screen.getId(), start, start.plusHours(2), 200.0);

        long seatId = fx.seats.get(2).getId();
        Reservation held = fx.reservationService.holdSeats(fx.regularUser, nearShowtime.getId(), List.of(seatId));
        Reservation confirmed = fx.reservationService.confirmReservation(fx.regularUser, held.getId());

        assertThrows(InvalidReservationStateException.class, () ->
            fx.reservationService.cancelReservation(fx.regularUser, confirmed.getId()));
    }

    @Test
    void cannotBookAShowtimeThatHasAlreadyStarted() {
        var pastStart = java.time.LocalDateTime.now().minusHours(3);
        // Scheduling itself only forbids inactive screens/overlaps, not past times -- an admin
        // may legitimately backfill historical data -- but booking a past show must still be
        // rejected at hold time.
        var pastShowtime = fx.schedulingService.createShowtime(
            fx.admin, fx.movie.getId(), fx.screen.getId(), pastStart, pastStart.plusHours(2), 200.0);
        long seatId = fx.seats.get(3).getId();
        assertThrows(ValidationException.class, () ->
            fx.reservationService.holdSeats(fx.regularUser, pastShowtime.getId(), List.of(seatId)));
    }
}
