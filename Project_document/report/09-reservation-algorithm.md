# Reservation Algorithm (Seat-Locking Strategy)

## The race condition
Two users, A and B, both attempt to hold seat A2 for the same showtime within
milliseconds of each other. A naive implementation checks "is A2 free?", sees yes for
both, and both then insert a booking -- producing two reservations for one seat.

## Why a naive check is unsafe
The gap between "check" and "write" is exactly where a second thread can land. No
amount of checking harder in application code closes that gap on its own; the check
and the write must be the same atomic operation for the guarantee to be real.

## The chosen solution
1. `reserved_seats` has a `UNIQUE(showtime_id, seat_id)` index (see `database/schema.sql`).
2. `holdSeats` performs an upfront read of currently-blocked seats purely to produce a
   fast, specific error message ("seat A2 already held or booked") -- **this read is
   not what guarantees correctness.**
3. The actual guarantee is the subsequent `INSERT INTO reserved_seats`: because the
   unique index exists, this insert is itself the atomic compare-and-insert. Whichever
   transaction's insert reaches SQLite's single-writer lock first succeeds; the second
   transaction's insert fails with `SQLITE_CONSTRAINT_UNIQUE` before a duplicate row
   can ever exist.
4. `holdSeats` catches that constraint violation specifically and re-throws it as
   `SeatUnavailableException("...just taken by another booking...")`.
5. Every write transaction (including this one) is opened with `BEGIN IMMEDIATE`
   (`Database.beginWriteTransaction`) rather than SQLite's default deferred mode --
   see `docs/report/07-design-decisions.md` item 4 for why: deferred transactions can
   contend badly when many threads try to upgrade a read lock to a write lock at once,
   which this project's own concurrency test caught during development.

## What happens when two users request the same seat (concrete trace)
1. Both transactions begin (`BEGIN IMMEDIATE` serializes them -- one proceeds, one waits).
2. The first transaction's `INSERT INTO reserved_seats` succeeds and commits.
3. The second transaction's `INSERT INTO reserved_seats` now sees an existing row for
   `(showtime_id, seat_id)` and fails with a unique-constraint violation.
4. `ReservationService` catches that `SQLException`, confirms it's a constraint
   violation (`isUniqueConstraintViolation`), rolls back, and throws
   `SeatUnavailableException` to the second caller.
5. The first caller receives a `Reservation` in `HELD` status; the second caller
   receives a clear rejection. At no point does more than one active reservation row
   exist for that seat/showtime pair.

## Automated proof
`ReservationConcurrencyTest.onlyOneOfManyConcurrentRequestsForTheSameSeatSucceeds`
spins up 12 threads, each a different registered user, all racing (via a
`CountDownLatch` starting gate) to hold the same seat on the same showtime. The test
asserts exactly 1 success and 11 `SeatUnavailableException` rejections, and separately
verifies via the database that exactly one reservation row exists across all 12 users
afterwards. It is repeated 5 times (`@RepeatedTest(5)`) to reduce the chance of a
timing coincidence masking a real bug. All 5 repetitions pass -- see `test_run.txt`.

A second test, `differentSeatsCanBeBookedConcurrentlyWithoutInterference`, confirms
the locking strategy does not over-serialize unrelated bookings: distinct seats on the
same showtime, booked concurrently by distinct users, all succeed.
