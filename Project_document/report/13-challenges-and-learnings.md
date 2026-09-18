# Challenges Faced

1. **No Maven Central access in the build/verification environment.** Discovered
   early, before any Spring Boot code was written. Forced (and justified) the pivot to
   plain JDBC + SQLite documented in `07-design-decisions.md` item 1. Resolved by
   vendoring every dependency as a local jar and building with `javac`/`java` directly.

2. **`SQLITE_BUSY` under real concurrent load.** The first version of
   `ReservationConcurrencyTest` intermittently failed -- not because the double-booking
   logic was wrong, but because SQLite's *default* deferred-transaction mode contends
   badly when many connections try to upgrade a read lock to a write lock at close to
   the same instant. Root-caused to SQLite's locking model specifically (not a generic
   "increase the timeout" band-aid), and fixed by switching every write transaction to
   `BEGIN IMMEDIATE` (`Database.beginWriteTransaction`). This is arguably the most
   valuable thing this project surfaced -- a real concurrency bug, caught by a real
   concurrency test, with a real (not superficial) fix.

3. **The Xerial SQLite JDBC driver's `getGeneratedKeys()` support.** Several DAO
   `insert` methods initially used the standard JDBC
   `prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)` pattern, which this
   driver does not fully support (`SQLFeatureNotSupportedException`). Fixed by
   querying `SELECT last_insert_rowid()` on the same connection immediately after
   each insert (`Database.lastInsertRowId`), which is SQLite's own reliable
   mechanism for this.

4. **A Linux-distribution-packaged `sqlite-jdbc.jar` silently missing Windows/Linux
   native libraries.** The jar initially vendored under `lib/` came from a Debian/apt
   package that turned out to bundle only macOS natives inside the jar itself,
   relying on a separately-installed OS package for the Linux native library --
   invisible while testing only on that one Linux machine, but a hard failure
   (`NativeLibraryNotFoundException`) for anyone on Windows. Fixed by replacing it
   with the official multi-platform jar from the Xerial project's own GitHub
   releases, verified to contain Windows, Linux, and macOS native libraries by
   inspecting the jar's contents directly rather than assuming. This is a good
   example of why "it works on my machine" isn't sufficient verification for a
   cross-platform deliverable -- see also item 1 above.

5. **Naive SQL-comment stripping.** The first schema-loading implementation split
   `schema.sql` on `;` without first removing `--` line comments, which broke because
   some comments in the schema themselves contain semicolons in prose. Fixed by
   stripping line comments before splitting on statement boundaries.

# Learnings

- A unique database constraint is a stronger, simpler correctness guarantee than any
  amount of "check carefully" application logic for a race condition -- and it's worth
  writing an actual multi-threaded test to prove it, not just asserting so in a comment.
- SQLite's concurrency model (single writer, deferred vs. immediate transactions) has
  real, non-obvious implications for a JDBC application that a purely
  PostgreSQL/MySQL background wouldn't necessarily surface.
- Environment constraints (like no Maven Central access) are worth investigating and
  designing around explicitly rather than assumed away -- the alternative was
  delivering code that had never actually been compiled.

# Limitations
See the "Known Limitations" section of the README.

# Future Enhancements
See the "Future Enhancements" section of the README.
