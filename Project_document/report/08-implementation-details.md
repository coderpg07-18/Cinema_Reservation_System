# Implementation Details

## Package layout
```
com.cinemareserve.domain      Plain domain objects + enums (no JPA annotations)
com.cinemareserve.dao         JDBC data access, one class per table/aggregate
com.cinemareserve.service     Business rules, transactions, authorization
com.cinemareserve.exception   Custom business exceptions
com.cinemareserve.security    BCrypt password hashing (PasswordHasher)
com.cinemareserve.db          Connection provider, schema bootstrap, transaction helper
com.cinemareserve.util        Validator, BookingReferenceGenerator
com.cinemareserve.cli         Main (console UI), AppContext (composition root), SeedData
```

## Notable implementation points
- **Composition root:** `AppContext` manually wires every DAO into its service. No DI
  framework -- at 8 DAOs / 6 services this is easy to read top-to-bottom, which matters
  for a viva.
- **Connection lifecycle:** one JDBC connection per unit of work, opened and closed
  with try-with-resources around each service-layer operation -- never held across
  multiple user interactions.
- **Schema bootstrap:** `Database.getConnection()` applies `database/schema.sql`
  (via the classpath resource `/schema.sql`) exactly once per process, using
  `IF NOT EXISTS` throughout so it's safe to call repeatedly.
- **Pricing:** `ReservationService.priceFor` adds a fixed surcharge for PREMIUM/RECLINER
  seats on top of the showtime's base price -- a small but real business rule, not
  just "price = base price for every seat."
- **Booking references:** `BookingReferenceGenerator` produces short, human-readable
  codes like `CR-7F3K9Q` using an alphabet that excludes visually ambiguous characters
  (0/O, 1/I).

## Known-good runtime environment
- Java 21 (any JDK 21 distribution)
- Dependencies vendored under `lib/`: `sqlite-jdbc.jar` (3.44.x), `jbcrypt.jar` (0.4),
  `slf4j-api.jar` + `slf4j-simple.jar` (1.7.x, required by the SQLite driver and used
  for the application's own logging), `junit-platform-console-standalone.jar` (for
  running tests without Maven).
