package com.cinemareserve.service;

import com.cinemareserve.TestDb;
import com.cinemareserve.TestFixtures;
import com.cinemareserve.domain.Showtime;
import com.cinemareserve.exception.AuthorizationException;
import com.cinemareserve.exception.ScheduleConflictException;
import com.cinemareserve.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingServiceTest {

    private TestFixtures fx;

    @BeforeEach
    void setUp() {
        TestDb.freshDatabase();
        fx = new TestFixtures();
    }

    @Test
    void createsValidShowtime() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(14).withMinute(0);
        LocalDateTime end = start.plusHours(2);
        Showtime s = fx.schedulingService.createShowtime(fx.admin, fx.movie.getId(), fx.screen.getId(), start, end, 200);
        assertNotNull(s.getId());
    }

    @Test
    void rejectsOverlappingShowtimeOnSameScreen() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(14).withMinute(0);
        LocalDateTime end = start.plusHours(2).plusMinutes(30); // 14:00 - 16:30
        fx.schedulingService.createShowtime(fx.admin, fx.movie.getId(), fx.screen.getId(), start, end, 200);

        // Second show starts at 15:30, well inside the first show's window -> must be rejected.
        LocalDateTime secondStart = start.plusMinutes(90);
        LocalDateTime secondEnd = secondStart.plusHours(2).plusMinutes(30);
        assertThrows(ScheduleConflictException.class, () ->
            fx.schedulingService.createShowtime(fx.admin, fx.movie.getId(), fx.screen.getId(), secondStart, secondEnd, 200));
    }

    @Test
    void allowsBackToBackShowtimesThatDoNotOverlap() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(14).withMinute(0);
        LocalDateTime end = start.plusHours(2); // 14:00 - 16:00
        fx.schedulingService.createShowtime(fx.admin, fx.movie.getId(), fx.screen.getId(), start, end, 200);

        // Second show starts exactly when the first ends -- must be allowed.
        Showtime second = fx.schedulingService.createShowtime(
            fx.admin, fx.movie.getId(), fx.screen.getId(), end, end.plusHours(2), 200);
        assertNotNull(second.getId());
    }

    @Test
    void rejectsInvalidTimeWindow() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertThrows(ValidationException.class, () ->
            fx.schedulingService.createShowtime(fx.admin, fx.movie.getId(), fx.screen.getId(), start, start.minusHours(1), 200));
    }

    @Test
    void regularUserCannotCreateShowtime() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        assertThrows(AuthorizationException.class, () ->
            fx.schedulingService.createShowtime(fx.regularUser, fx.movie.getId(), fx.screen.getId(), start, start.plusHours(2), 200));
    }
}
