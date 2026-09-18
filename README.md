# CinemaReserve -- Cinema Booking & Reservation Management System

## Overview
CinemaReserve is a console-based cinema reservation platform built in plain Java 21
with JDBC and SQLite (no framework). It was built for a VITyarthi "Build Your Own
Project" submission, and its technical center of gravity is the reservation engine:
guaranteeing no seat is ever double-booked under concurrent requests, and that a
screen can never host two overlapping shows.

## Problem Statement
See [statement.md](statement.md) for the full academic problem statement.

## Objectives
- Enforce correct, race-condition-free seat reservation.
- Enforce scheduling-conflict-free showtimes per screen.
- Provide role-separated (USER / ADMIN) functionality with real authorization checks.
- Provide operational reporting via SQL aggregation.
- Keep every business rule covered by an automated test, including a genuine
  multi-threaded concurrency test for the double-booking guarantee.

## Features
- Registration & login with BCrypt-hashed passwords
- Movie catalog: create/update/deactivate, search/filter by title, genre, language
- Theatre / screen / seat management, including bulk seat-grid generation
- Showtime scheduling with interval-overlap conflict detection
- Seat map per showtime: AVAILABLE / HELD / BOOKED (master seat record never mutated)
- Seat hold (10-minute expiry, server-enforced) -> confirm -> cancel workflow
- Cancellation policy: HELD reservations cancellable any time before the show;
  CONFIRMED reservations cancellable up to 60 minutes before the show
- Admin reports: reservation summary, revenue by movie, screen occupancy
- Centralized, friendly error handling -- no stack traces ever reach the user

## Functional Requirements
See [docs/report/04-functional-requirements.md](docs/report/04-functional-requirements.md).

## Non-Functional Requirements
See [docs/report/05-non-functional-requirements.md](docs/report/05-non-functional-requirements.md).

## Architecture

```
CLI (Main.java)
     |
Service Layer   (business rules, transactions, authorization)
     |
DAO Layer       (JDBC, one class per table/aggregate)
     |
SQLite database (data/cinemareserve.db)
```

Domain classes are plain Java objects, not JPA entities -- there is no ORM in this
project by design (see "Technology Stack" below for why). See
[docs/diagrams/system-architecture.mmd](docs/diagrams/system-architecture.mmd) for the
full component diagram.

## Technology Stack

**Java 21, plain JDBC, SQLite, jBCrypt, JUnit 5. No Spring, no Maven-Central-hosted
framework.**

This is a deliberate pivot from the more common Spring Boot + PostgreSQL combination,
and the reasoning is worth stating plainly for the viva: the environment this project
was built and verified in has no access to Maven Central, so a Spring Boot dependency
tree simply cannot be downloaded there. Rather than hand over code that was never
actually compiled or run, the project uses a stack built entirely from locally
available Java libraries -- every dependency below ships as a plain jar under `lib/`,
so **building this project requires no internet access at all**, which is also a
genuine advantage for a reliable classroom/viva demo.

| Concern | Choice | Why |
|---|---|---|
| Language/runtime | Java 21 | Current LTS, records/switch expressions used throughout |
| Persistence | SQLite via the Xerial JDBC driver | Real relational DB, real transactions, real `UNIQUE`/`CHECK` constraints, zero server setup |
| Password hashing | jBCrypt | Industry-standard adaptive hashing, small and dependency-free |
| Testing | JUnit 5 (run via the console-standalone launcher) | Modern assertions, `@RepeatedTest` used for the concurrency test |
| Build | Plain `javac`/`java` via shell scripts in `scripts/` | No build-tool download required; a `pom.xml` is still provided (see below) for anyone with Maven Central access |

A `pom.xml` is included in the repository root for completeness and portability --
if you *do* have Maven Central access, `mvn compile` will also work against the same
source tree. It is not required and was not the primary way this project was built
or verified.

### Persistence model note
SQLite has a single-writer concurrency model (readers can run concurrently in WAL
mode, but only one write transaction proceeds at a time). The reservation and
scheduling services explicitly open write transactions with `BEGIN IMMEDIATE`
(see `Database.beginWriteTransaction`) rather than relying on JDBC's default deferred
transaction, specifically to avoid a lock-upgrade contention failure that a real
12-thread concurrency test in this project's own test suite surfaced during
development -- see `ReservationConcurrencyTest` and the code comments on
`ReservationService.holdSeats` for the full explanation.

### A note on `lib/sqlite-jdbc.jar` specifically
This jar must be the **official multi-platform build** published by the Xerial
project (from Maven Central or the project's GitHub releases), which bundles native
libraries for Windows, Linux, and macOS inside the same jar. A Linux-distribution
repackaged version of this library (e.g. an `apt`/`libxerial-sqlite-jdbc-java`
package) may strip out every native library except the one for that distribution's
own platform, relying instead on a separate OS-level package for the native part --
which works fine on that one Linux machine but fails everywhere else with
`NativeLibraryNotFoundException` / `UnsatisfiedLinkError`. If you ever replace this
jar, verify it contains `org/sqlite/native/Windows/...`, `org/sqlite/native/Linux/...`,
and `org/sqlite/native/Mac/...` entries (`unzip -l lib/sqlite-jdbc.jar | grep native`)
before assuming it will work cross-platform.

## System Workflow
See the sequence diagrams in `docs/diagrams/`:
- `sequence-hold-and-confirm.mmd` -- seat hold -> confirm
- `sequence-scheduling-conflict.mmd` -- admin showtime creation with conflict rejection
- `reservation-workflow.mmd` -- the reservation state machine (HELD/CONFIRMED/CANCELLED/EXPIRED)

## Database Design
Full schema: [database/schema.sql](database/schema.sql). ER diagram:
[docs/diagrams/er-diagram.mmd](docs/diagrams/er-diagram.mmd).

The key design decision: a showtime-specific seat's "booked" state lives in the
`reserved_seats` table, joined through `reservations`, **not** as a status column
on the master `seats` row. A seat is a physical thing that exists once; whether it's
taken is a fact about one particular showtime. The `UNIQUE(showtime_id, seat_id)`
index on `reserved_seats` is what makes double-booking impossible at the database
level, independent of any application-level check (see "Reservation/Seat-Locking
Strategy" below).

## Installation

### Prerequisites
- Java 21 JDK (`javac -version` should print `javac 21...`)
- No Maven, no internet connection, and no separate database server required.
  (A `pom.xml` is provided as an alternative path if you do have Maven + Maven
  Central access -- see "Technology Stack" above.)

### Get the dependencies
All required jars are already vendored under `lib/`:
`sqlite-jdbc.jar`, `jbcrypt.jar`, `slf4j-api.jar`, `slf4j-simple.jar`,
`junit-platform-console-standalone.jar`. Nothing to download.

## Configuration
See [.env.example](.env.example). The only setting currently read is
`CINEMARESERVE_DB_PATH`, which overrides where the SQLite file is created
(defaults to `data/cinemareserve.db`).

## Database Setup
There is no separate setup step -- `database/schema.sql` is applied automatically
the first time the application opens a database connection (see `Database.java`).
To start completely fresh:
```
./scripts/reset-db.sh
```

## Running the Application

```bash
./scripts/build.sh   # compiles main + test sources into target/
./scripts/seed.sh     # loads a development admin account, a demo user, and a sample catalog
./scripts/run.sh      # starts the interactive CLI
```

### Sample Credentials
Created by `scripts/seed.sh`, for local development/demo use only:

| Role | Username | Password |
|---|---|---|
| Admin | `admin` | `Admin@123` |
| Customer | `alice` | `Password1` |

These are documented development seed credentials, never a production secret --
only their BCrypt hashes are ever stored in the database.

## Test Execution
```bash
./scripts/test.sh
```
This runs the full JUnit 5 suite (36 tests) and writes the results to
[test_run.txt](test_run.txt), including five repetitions of a genuine 12-thread
concurrency test proving the double-booking guarantee. See
[docs/report/12-testing-approach.md](docs/report/12-testing-approach.md) for what
each test class covers.

## Troubleshooting

**Windows: `package com.cinemareserve.dao does not exist` / `package org.junit...does not exist` during `build.sh`.**
Java's `-cp` argument uses `:` to separate classpath entries on Linux/macOS but `;`
on Windows -- this is the JVM's own rule, not the shell's, so it applies whether
you're running the scripts from Git Bash, WSL, or cmd.exe. The scripts in `scripts/`
detect this automatically via `scripts/_env.sh` (checked with `uname -s`); if you
still hit this error, you likely have an older copy of the scripts -- re-extract the
project zip, or manually replace every `:` between classpath entries with `;` when
running the underlying `javac -cp "lib/*;target/classes" ...` / `java -cp "..." ...`
commands shown inside each `.sh` file.

**`./scripts/build.sh: command not found` or similar on Windows.**
The scripts are bash scripts and need a bash-compatible shell -- Git Bash (ships
with Git for Windows) or WSL both work; plain `cmd.exe`/PowerShell do not run `.sh`
files directly. Run them via Git Bash, or open WSL and `cd` to the project there.

**Windows: `NativeLibraryNotFoundException` / `UnsatisfiedLinkError: ... _open_utf8 ...` when running tests or the app.**
This means `lib/sqlite-jdbc.jar` doesn't contain a native library for Windows --
see "A note on lib/sqlite-jdbc.jar specifically" above. Re-download the jar from
https://github.com/xerial/sqlite-jdbc/releases (pick the plain
`sqlite-jdbc-<version>.jar` asset, not a `-sources` or `-javadoc` one) and replace
`lib/sqlite-jdbc.jar` with it.

## CLI Usage
On launch you'll see a login/register menu. After logging in as a customer you get:
browse/search movies, view showtimes for a movie+date, view a showtime's seat map and
hold seats, view your reservations, confirm a held reservation, cancel a reservation.
After logging in as an admin you get: add movie, add theatre, add screen, generate a
seat grid for a screen, create a showtime, list upcoming showtimes, and view reports.
Every screen shows numbered options; invalid input produces a friendly message, never
a stack trace.

## Screenshots
This is a CLI application. See
[docs/report/11-screenshots.md](docs/report/11-screenshots.md) for the exact list of
terminal screenshots to capture for the submission (login, booking flow, a rejected
double-booking, a rejected scheduling conflict, admin reports, and the test run).

## Project Structure
```
cinemareserve/
├── src/main/java/com/cinemareserve/
│   ├── domain/       plain domain objects + enums
│   ├── dao/          JDBC data access, one class per table/aggregate
│   ├── service/       business rules, transactions, authorization
│   ├── exception/     custom business exceptions
│   ├── security/      BCrypt password hashing
│   ├── db/            connection + schema bootstrap + transaction helper
│   ├── util/          validation, booking reference generator
│   └── cli/           console UI (Main, AppContext, SeedData)
├── src/test/java/com/cinemareserve/   JUnit 5 tests (service + concurrency)
├── database/schema.sql
├── docs/
│   ├── diagrams/      Mermaid source for all UML/ER/workflow diagrams
│   └── report/        project report source, section by section
├── scripts/           build.sh, run.sh, seed.sh, test.sh, reset-db.sh
├── lib/               vendored dependency jars (no internet needed to build)
├── sample-data/       notes on the seed data scripts/seed.sh loads
├── pom.xml            provided for Maven-Central-connected environments
├── README.md, statement.md, VIVA.md, test_run.txt, .gitignore, .env.example
```

## Security Considerations
- Passwords are hashed with BCrypt (work factor 12); plaintext passwords are never stored or logged.
- All admin-only operations go through `AuthorizationGuard`/inline role checks --
  a regular user calling an admin service method gets `AuthorizationException`, verified
  by tests (`SchedulingServiceTest.regularUserCannotCreateShowtime`,
  `ReportingServiceTest.regularUserCannotAccessReports`).
- No secrets are hardcoded for production use; the only credential shipped is the
  clearly-documented local development seed account.
- SQL is 100% parameterized (`PreparedStatement`) -- no string-concatenated queries anywhere.
- Errors shown to the user are friendly, mapped messages; the CLI's central `act()`
  handler never lets a raw exception/stack trace reach the console.

## Known Limitations
- No web/GUI front end -- this is a console application. The service layer is
  UI-agnostic and could back a REST API or web UI without modification.
- No real payment gateway -- confirming a reservation is treated as instant successful payment.
- Hold expiry is checked lazily (on the next reservation-touching call), not by a
  background scheduler -- documented and intentional for a single-process console
  app; a production deployment would add a scheduled sweep.
- SQLite's single-writer model means write throughput is lower than a true
  client-server database under very heavy concurrent load; this is an accepted
  trade-off for local demonstrability (see "Technology Stack" above).

## Future Enhancements
- REST API layer over the existing service layer (the service layer was written to
  not assume a CLI caller, so this is additive, not a rewrite)
- Real payment gateway integration
- Email/SMS notifications on confirmation and cancellation
- A scheduled background job for hold expiry instead of lazy sweeping
- Seat-map visualization in a web front end

## References
- SQLite transaction/locking documentation (`BEGIN IMMEDIATE` vs deferred transactions)
- jBCrypt / BCrypt password hashing scheme
- JUnit 5 User Guide (`@RepeatedTest`, `ExecutorService`-based concurrency testing pattern)
