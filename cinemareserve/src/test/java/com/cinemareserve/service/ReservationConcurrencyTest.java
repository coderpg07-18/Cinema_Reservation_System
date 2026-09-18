package com.cinemareserve.service;

import com.cinemareserve.TestDb;
import com.cinemareserve.TestFixtures;
import com.cinemareserve.domain.Reservation;
import com.cinemareserve.domain.ReservationStatus;
import com.cinemareserve.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the double-booking guarantee under actual concurrent access rather than just
 * asserting it in comments. N threads race to hold the SAME seat for the SAME showtime
 * at (as close as the JVM allows to) the same instant. Exactly one must win; every other
 * thread must receive SeatUnavailableException, never a silently-swallowed failure and
 * never a second successful hold.
 */
class ReservationConcurrencyTest {

    private TestFixtures fx;

    @BeforeEach
    void setUp() {
        TestDb.freshDatabase();
        fx = new TestFixtures();
        fx.createShowtime(48, 200.0);
    }

    @RepeatedTest(5)
    void onlyOneOfManyConcurrentRequestsForTheSameSeatSucceeds() throws InterruptedException {
        int threadCount = 12;
        long seatId = fx.seats.get(0).getId();
        long showtimeId = fx.showtime.getId();

        User[] users = new User[threadCount];
        for (int i = 0; i < threadCount; i++) {
            users[i] = fx.authService.register("racer" + i + "_" + System.nanoTime(),
                    "racer" + i + "_" + System.nanoTime() + "@example.com", "RacerPass1");
        }

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            User user = users[i];
            futures.add(pool.submit(() -> {
                try {
                    startGate.await(); // release every thread at (as close as possible to) once
                    fx.reservationService.holdSeats(user, showtimeId, List.of(seatId));
                    successCount.incrementAndGet();
                } catch (com.cinemareserve.exception.SeatUnavailableException expected) {
                    rejectedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startGate.countDown(); // fire the starting gun
        for (Future<?> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                throw new AssertionError("worker thread failed unexpectedly", e);
            }
        }
        pool.shutdown();

        assertEquals(1, successCount.get(), "exactly one concurrent request for the same seat must succeed");
        assertEquals(threadCount - 1, rejectedCount.get(), "every other concurrent request must be rejected as unavailable");

        long activeReservationsForSeat = java.util.Arrays.stream(users)
                .mapToLong(u -> fx.reservationService.listForUser(u).size())
                .sum();
        assertEquals(1, activeReservationsForSeat, "exactly one reservation row should exist across all racing users");
    }

    @Test
    void differentSeatsCanBeBookedConcurrentlyWithoutInterference() throws InterruptedException {
        int threadCount = fx.seats.size();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            long seatId = fx.seats.get(i).getId();
            User user = fx.authService.register("buyer" + i + "_" + System.nanoTime(),
                    "buyer" + i + "_" + System.nanoTime() + "@example.com", "BuyerPass1");
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    Reservation r = fx.reservationService.holdSeats(user, fx.showtime.getId(), List.of(seatId));
                    if (r.getStatus() == ReservationStatus.HELD) successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                throw new AssertionError("worker thread failed unexpectedly", e);
            }
        }
        pool.shutdown();

        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(threadCount, successCount.get(), "booking distinct seats concurrently must never fail on each other");
    }
}
