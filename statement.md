# statement.md

## Project Title
CinemaReserve -- Cinema Booking & Reservation Management System

## Problem Statement
Manual or naively-built cinema booking systems commonly fail at the one thing that
actually matters technically: guaranteeing a seat sold to one customer cannot also be
sold to another. They also frequently allow a screen to be double-booked with two
overlapping shows, or let a cancellation happen after a film has already started.
CinemaReserve is a backend reservation platform built to solve these three problems
correctly -- with database-enforced seat uniqueness, interval-overlap scheduling
conflict detection, and an explicit, tested cancellation policy -- on top of standard
movie-browsing, booking, and admin catalog/reporting functionality.

## Scope
In scope: user registration/login, role-based access (USER/ADMIN), movie catalog
management, theatre/screen/seat management, showtime scheduling with conflict
detection, seat-level availability, temporary seat holds, reservation confirmation,
cancellation with a configurable window, and operational reporting (revenue,
occupancy, popularity) computed via SQL aggregation.

Out of scope: a graphical/web UI (this is a console application backed by a real
service/DAO layer, documented for anyone building a UI on top of it later), real
payment gateway integration (booking confirmation is treated as instant payment
success), and infrastructure concerns (Docker/Kubernetes/microservices) not
justified at this project's scale.

## Target Users
- **Customers** who want to browse movies, check showtimes, and reserve specific seats.
- **Cinema administrators** who manage the movie/theatre/screen catalog, create
  showtimes, and monitor bookings and revenue.

## High-Level Features
- Secure registration and login (BCrypt password hashing, role-based authorization)
- Movie catalog with search/filter and soft deactivation (historical reservations
  are never broken by a movie being taken off the schedule)
- Theatre / screen / seat management, including auto-generated seat grids
- Showtime scheduling that rejects any overlapping show on the same screen
- A showtime-specific seat map (available / held / booked) that never mutates the
  master seat record
- Temporary seat holds with a server-enforced expiry, confirmation, and a
  cancellation policy that is actually enforced, not just documented
- Admin reporting: reservation totals, revenue by movie, screen occupancy

## Main Modules
1. Identity & Access (`AuthService`, `PasswordHasher`, `AuthorizationGuard`)
2. Movie & Venue Catalog (`MovieService`, `VenueService`)
3. Scheduling (`SchedulingService`)
4. Reservation Engine (`ReservationService`) -- the core of the project
5. Reporting (`ReportingService`)

## Expected Outcome
A runnable, tested Java console application, backed by a real (file-based) SQLite
database, that a B.Tech CS student can build, run, and explain end-to-end during a
viva -- including how it guarantees no seat is ever double-booked under concurrent
requests, which this project verifies with an actual multi-threaded JUnit test
rather than by argument alone.
