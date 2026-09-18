# System Architecture

CinemaReserve follows a strict four-layer architecture:

```
CLI (Main.java, AppContext, SeedData)
        |
Service Layer (AuthService, MovieService, VenueService,
               SchedulingService, ReservationService, ReportingService)
        |
DAO Layer (UserDao, MovieDao, GenreDao, TheatreDao, ScreenDao,
           SeatDao, ShowtimeDao, ReservationDao)
        |
SQLite database (data/cinemareserve.db)
```

See [docs/diagrams/system-architecture.mmd](../diagrams/system-architecture.mmd) for
the full component diagram, and [docs/diagrams/component-diagram.mmd](../diagrams/component-diagram.mmd).

## Layer responsibilities

**CLI layer.** Pure presentation: reads input, calls exactly one service method per
action, prints the result or a friendly error. Contains no business logic and no SQL.

**Service layer.** Owns every business rule: validation, authorization, transaction
boundaries, and the reservation/scheduling algorithms. Every method that performs more
than one write opens an explicit transaction (`Database.beginWriteTransaction`) and
rolls back on any exception. Services depend only on DAOs and domain classes, never on
the CLI.

**DAO layer.** One class per aggregate root/table. Every query is parameterized
(`PreparedStatement`); no string concatenation of user input into SQL anywhere in the
codebase. DAOs are intentionally "dumb" -- they map result sets to domain objects and
nothing else; no business rules live here.

**Database.** A single SQLite file. Constraints (`UNIQUE`, `CHECK`, `FOREIGN KEY`) are
treated as a second line of defense, not decoration -- the seat double-booking
guarantee specifically depends on the database rejecting a duplicate
`(showtime_id, seat_id)` row even if the application-level check were ever buggy.

## Transaction boundaries
A transaction boundary is exactly one service method that performs a write:
`holdSeats`, `confirmReservation`, `cancelReservation`, `createShowtime`. Each opens a
connection, calls `Database.beginWriteTransaction` (see "Design Decisions" for why
`BEGIN IMMEDIATE` specifically), performs its reads/writes, and either commits once at
the end or rolls back in a catch block. No transaction spans more than one service
method call.

## Security flow
1. `AuthService.login` looks up the user, checks `active`, and verifies the password
   with `PasswordHasher.matches` (BCrypt).
2. The CLI holds the resulting `User` object as `currentUser` for the session.
3. Every admin-only service method takes the acting `User` as its first parameter and
   calls `AuthorizationGuard.requireAdmin` (or an equivalent inline check) before doing
   anything else -- authorization is checked in the service layer, not the CLI, so it
   cannot be bypassed by a different caller.

## Reservation flow
See [docs/diagrams/sequence-hold-and-confirm.mmd](../diagrams/sequence-hold-and-confirm.mmd)
for the full sequence diagram of `holdSeats` -> `confirmReservation`.
