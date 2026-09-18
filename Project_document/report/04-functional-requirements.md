# Functional Requirements

## Customer-facing
1. Register a new account with a unique username and email.
2. Log in with username/password; inactive accounts are rejected.
3. Browse and search movies by title (partial match), with genre/language filters
   supported at the service layer.
4. View showtimes for a chosen movie and date.
5. View a showtime's seat map, distinguishing AVAILABLE, HELD, and BOOKED seats.
6. Hold one or more specific seats for a showtime.
7. Confirm a held reservation before its hold expires.
8. View a list of all of one's own reservations with status and seats.
9. Cancel an eligible upcoming reservation.

## Admin-facing
10. Create, update, and deactivate movies (deactivation is soft -- historical
    reservations against a deactivated movie remain intact and reportable).
11. Create theatres, screens (with a fixed seat capacity), and seats -- including
    bulk seat-grid generation for a screen.
12. Create showtimes; the system rejects any showtime that would overlap an
    existing SCHEDULED showtime on the same screen.
13. List all upcoming showtimes.
14. View operational reports: reservation summary, revenue by movie, and screen
    occupancy percentage.

## Cross-cutting
15. Every write operation runs inside a database transaction; nothing is
    partially applied on failure (verified by explicit rollback-on-exception paths
    in every service method that writes more than one row).
16. Every business-rule violation raises a specific, named exception
    (`ValidationException`, `AuthorizationException`, `SeatUnavailableException`,
    `ScheduleConflictException`, `InvalidReservationStateException`,
    `NotFoundException`, `AuthenticationException`) that the CLI maps to a clear,
    single-line message -- never a raw stack trace.
