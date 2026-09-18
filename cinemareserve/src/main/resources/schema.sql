-- CinemaReserve database schema (SQLite)
-- Foreign keys must be turned on per-connection: PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT NOT NULL UNIQUE,
    email           TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    active          INTEGER NOT NULL DEFAULT 1,
    created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS genres (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    name    TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS movies (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    title           TEXT NOT NULL,
    description     TEXT,
    duration_mins   INTEGER NOT NULL CHECK (duration_mins > 0),
    genre_id        INTEGER NOT NULL REFERENCES genres(id),
    language        TEXT NOT NULL,
    release_date    TEXT,
    poster_ref      TEXT,
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS theatres (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    location    TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE IF NOT EXISTS screens (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    theatre_id      INTEGER NOT NULL REFERENCES theatres(id),
    screen_name     TEXT NOT NULL,
    capacity        INTEGER NOT NULL CHECK (capacity > 0),
    status          TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    UNIQUE (theatre_id, screen_name)
);

CREATE TABLE IF NOT EXISTS seats (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    screen_id       INTEGER NOT NULL REFERENCES screens(id),
    seat_row        TEXT NOT NULL,
    seat_number     INTEGER NOT NULL CHECK (seat_number > 0),
    seat_type       TEXT NOT NULL DEFAULT 'STANDARD' CHECK (seat_type IN ('STANDARD', 'PREMIUM', 'RECLINER')),
    UNIQUE (screen_id, seat_row, seat_number)
);

-- A showtime is one screening of one movie, on one screen, in one time window.
CREATE TABLE IF NOT EXISTS showtimes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    movie_id        INTEGER NOT NULL REFERENCES movies(id),
    screen_id       INTEGER NOT NULL REFERENCES screens(id),
    start_time      TEXT NOT NULL,   -- ISO-8601 local datetime, e.g. 2026-09-20T14:00:00
    end_time        TEXT NOT NULL,
    base_price      REAL NOT NULL CHECK (base_price >= 0),
    status          TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_showtimes_screen_time ON showtimes(screen_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_showtimes_movie ON showtimes(movie_id);

-- One reservation = one user's booking attempt for a showtime (0..n seats attached via reserved_seats).
CREATE TABLE IF NOT EXISTS reservations (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    booking_reference   TEXT NOT NULL UNIQUE,
    user_id             INTEGER NOT NULL REFERENCES users(id),
    showtime_id         INTEGER NOT NULL REFERENCES showtimes(id),
    status              TEXT NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    total_amount        REAL NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    created_at          TEXT NOT NULL DEFAULT (datetime('now')),
    confirmed_at        TEXT,
    cancelled_at        TEXT,
    hold_expires_at     TEXT
);

CREATE INDEX IF NOT EXISTS idx_reservations_user ON reservations(user_id);
CREATE INDEX IF NOT EXISTS idx_reservations_showtime ON reservations(showtime_id);

-- The showtime-specific seat state. A row here existing (in HELD or CONFIRMED reservation)
-- is what makes a seat unavailable for that showtime -- the master `seats` row is never touched.
-- The UNIQUE constraint is the final, database-enforced guarantee against double booking:
-- two reservations can never both hold an ACTIVE (non-cancelled/expired) row for the same
-- (showtime_id, seat_id) pair.
CREATE TABLE IF NOT EXISTS reserved_seats (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    reservation_id  INTEGER NOT NULL REFERENCES reservations(id),
    showtime_id     INTEGER NOT NULL REFERENCES showtimes(id),
    seat_id         INTEGER NOT NULL REFERENCES seats(id),
    price           REAL NOT NULL CHECK (price >= 0)
);

-- Partial unique index: only rows belonging to a HELD or CONFIRMED reservation block the seat.
-- Enforced in application logic at insert time (see ReservationService) since SQLite's
-- partial-index-on-join isn't practical here; the plain UNIQUE below prevents two ACTIVE rows
-- for the same seat/showtime because cancelled/expired reserved_seats rows are deleted, not kept.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reserved_seat_active ON reserved_seats(showtime_id, seat_id);

CREATE INDEX IF NOT EXISTS idx_reserved_seats_reservation ON reserved_seats(reservation_id);
