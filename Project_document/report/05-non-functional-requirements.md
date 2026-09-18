# Non-Functional Requirements

| Requirement | How it is addressed | Evidence |
|---|---|---|
| **Reliability / correctness under concurrency** | Seat holds/confirmations run in `BEGIN IMMEDIATE` write transactions; the `UNIQUE(showtime_id, seat_id)` index on `reserved_seats` is the final, DB-enforced guarantee against double booking. | `ReservationConcurrencyTest` -- 12 threads racing for one seat, 5 repetitions, 0 double bookings across all runs. |
| **Security** | BCrypt password hashing (work factor 12); role-based authorization on every admin-only service method; 100% parameterized SQL. | `AuthServiceTest`, `SchedulingServiceTest.regularUserCannotCreateShowtime`, `ReportingServiceTest.regularUserCannotAccessReports`. |
| **Maintainability** | Strict layering (CLI -> Service -> DAO -> DB); one exception type per failure category instead of generic `RuntimeException`; no class exceeds a few hundred lines; no framework "magic" to trace through. | Package structure under `src/main/java/com/cinemareserve/`. |
| **Usability** | Centralized CLI error handling (`Main.act()`) converts every business exception into a one-line, human-readable message. | `Main.java` `act()` method. |
| **Performance** | Reports use SQL `GROUP BY`/`SUM`/`COUNT` aggregation, not in-memory loops over all rows; indexes on `showtimes(screen_id, start_time, end_time)`, `reservations(user_id)`, `reservations(showtime_id)`. | `database/schema.sql`, `ReportingService`. |
| **Data integrity** | Foreign keys enforced (`PRAGMA foreign_keys = ON`); `CHECK` constraints on statuses, durations, prices, time ordering; `UNIQUE` constraints on usernames, emails, booking references, and seat/showtime pairs. | `database/schema.sql`. |
| **Error handling** | Every service method that can fail raises a specific exception; every write transaction is wrapped in try/catch/rollback. | `ReservationService`, `SchedulingService`. |
| **Resource efficiency** | One JDBC connection per unit of work (open, use, close) rather than holding connections indefinitely; SQLite WAL mode for concurrent reads. | `Database.getConnection()`. |
