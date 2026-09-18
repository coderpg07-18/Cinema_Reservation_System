# Conclusion

CinemaReserve implements the functional and non-functional requirements set out in
this report using a deliberately minimal, transparent technology stack. Its central
engineering claim -- that seat reservations cannot be double-booked even under
genuine concurrent access, and that showtimes cannot overlap on the same screen -- is
backed by an automated, repeatable test rather than by design-document assertion
alone, and the one real concurrency bug the project surfaced during its own
development (SQLite lock-upgrade contention) was root-caused and fixed rather than
patched over. The service layer is UI-agnostic by construction, so the project's
natural next step -- a REST API or web front end -- is additive rather than a rewrite.

# References
- SQLite documentation: Transaction and locking behavior --
  https://www.sqlite.org/lang_transaction.html, https://www.sqlite.org/lockingv3.html
- Xerial sqlite-jdbc driver documentation -- https://github.com/xerial/sqlite-jdbc
- jBCrypt -- https://www.mindrot.org/projects/jBCrypt/
- JUnit 5 User Guide -- https://junit.org/junit5/docs/current/user-guide/
- Oracle Java 21 documentation -- https://docs.oracle.com/en/java/javase/21/
