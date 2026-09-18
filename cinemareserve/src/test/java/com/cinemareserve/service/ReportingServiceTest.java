package com.cinemareserve.service;

import com.cinemareserve.TestDb;
import com.cinemareserve.TestFixtures;
import com.cinemareserve.domain.Reservation;
import com.cinemareserve.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportingServiceTest {

    private TestFixtures fx;

    @BeforeEach
    void setUp() {
        TestDb.freshDatabase();
        fx = new TestFixtures();
        fx.createShowtime(48, 200.0);
    }

    @Test
    void summaryCountsOnlyConfirmedTicketsAndRevenue() {
        // One confirmed booking of 2 seats, one held (not confirmed) booking of 1 seat --
        // the held one must not count towards tickets/revenue.
        Reservation confirmed = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(),
                List.of(fx.seats.get(0).getId(), fx.seats.get(1).getId()));
        fx.reservationService.confirmReservation(fx.regularUser, confirmed.getId());

        var otherUser = fx.authService.register("holder", "holder@example.com", "HolderPass1");
        fx.reservationService.holdSeats(otherUser, fx.showtime.getId(), List.of(fx.seats.get(2).getId()));

        ReportingService.SummaryReport summary = fx.reportingService.summary(fx.admin);
        assertEquals(2, summary.totalTicketsSold());
        assertEquals(400.0, summary.totalRevenue(), 0.001);
        assertEquals(1, summary.confirmedReservations());
    }

    @Test
    void revenueByMovieAggregatesCorrectly() {
        Reservation r = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(),
                List.of(fx.seats.get(0).getId()));
        fx.reservationService.confirmReservation(fx.regularUser, r.getId());

        List<ReportingService.MovieRevenueRow> rows = fx.reportingService.revenueByMovie(fx.admin);
        assertEquals(1, rows.size());
        assertEquals(fx.movie.getTitle(), rows.get(0).title());
        assertEquals(200.0, rows.get(0).revenue(), 0.001);
        assertEquals(1, rows.get(0).ticketsSold());
    }

    @Test
    void screenUtilizationComputesOccupancyPercentage() {
        // fixture screen has 10 seats (2 rows x 5).
        Reservation r = fx.reservationService.holdSeats(fx.regularUser, fx.showtime.getId(),
                List.of(fx.seats.get(0).getId(), fx.seats.get(1).getId()));
        fx.reservationService.confirmReservation(fx.regularUser, r.getId());

        List<ReportingService.ScreenUtilizationRow> rows = fx.reportingService.screenUtilization(fx.admin);
        var row = rows.stream().filter(x -> x.screenName().equals(fx.screen.getScreenName())).findFirst().orElseThrow();
        assertEquals(10, row.totalSeats());
        assertEquals(2, row.ticketsSold());
        assertEquals(20.0, row.occupancyPercent(), 0.001);
    }

    @Test
    void regularUserCannotAccessReports() {
        assertThrows(AuthorizationException.class, () -> fx.reportingService.summary(fx.regularUser));
    }
}
