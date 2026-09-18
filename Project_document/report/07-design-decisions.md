# Design Decisions & Rationale

## 1. Plain JDBC + SQLite instead of Spring Boot + PostgreSQL
The brief allowed either. The sandbox this project was actually built and tested in
has no access to Maven Central, so Spring Boot could not be downloaded there -- rather
than deliver untested code, the project was built entirely from locally available
jars. This turned out to have a genuine upside beyond necessity: zero external
services are required to build or run the project, which makes it more reliable to
demo in a viva than a stack requiring a running PostgreSQL server.

## 2. Master seat vs. showtime-specific seat state
A `Seat` row represents a physical seat that exists once, independent of any
showtime. Whether that seat is taken is a fact about one particular showtime, not
about the seat itself -- so "booked" state lives in `reserved_seats` (joined through
`reservations`), never as a mutable column on `seats`. This is what allows the same
physical seat A1 to be AVAILABLE for tomorrow's 6pm show and BOOKED for tonight's 9pm
show simultaneously.

## 3. `UNIQUE(showtime_id, seat_id)` as the real double-booking guarantee
See the extended comment on `ReservationService.holdSeats` and
`docs/report/09-reservation-algorithm.md` for the full explanation: the unique index
is deliberately the mechanism of correctness, not the upfront availability check
(which exists only for a fast, friendly error message).

## 4. `BEGIN IMMEDIATE` instead of JDBC's default deferred transaction
Discovered directly during development: the first version of the concurrency test
(`ReservationConcurrencyTest`) intermittently failed with `SQLITE_BUSY` under 12
concurrent writers. The cause was SQLite's deferred-transaction lock-upgrade
contention -- multiple connections each start as a reader and then race to upgrade
to a writer, which plain `busy_timeout` retries don't resolve cleanly. The fix was to
have every write transaction acquire the write lock up front with `BEGIN IMMEDIATE`
(`Database.beginWriteTransaction`), so concurrent writers queue for the lock instead
of racing during an upgrade. After the fix, all 5 repetitions of the concurrency test
pass reliably. This is documented here rather than hidden because it's a genuine,
useful finding about SQLite's concurrency model.

## 5. Soft deletion for movies
`Movie.status` is `ACTIVE`/`INACTIVE` rather than the row being deleted, specifically
so a historical reservation referencing a since-deactivated movie is never broken and
still appears correctly in reports.

## 6. Custom exception hierarchy instead of generic exceptions
Each failure category (`ValidationException`, `AuthorizationException`,
`SeatUnavailableException`, `ScheduleConflictException`,
`InvalidReservationStateException`, `NotFoundException`, `AuthenticationException`)
is a distinct type so the CLI's central error handler (`Main.act()`) can map each to
an appropriate message without string-matching on exception text.

## 7. No ORM
With plain JDBC, every query is visible and traceable in the DAO that issues it --
there is no auto-generated SQL to debug during a viva. Given the project's scale
(8 tables), the mapping code this requires is small and was judged worth the
trade-off for transparency.
