# Screenshots

This is a CLI application; screenshots are terminal captures. Not fabricated here --
this is the exact list to capture for the submission, matching the actual menu flow
in `Main.java`.

1. Application startup banner (`./scripts/run.sh`)
2. Registration flow (option 2 from the login menu)
3. Successful login as `alice` (customer)
4. Browse/search movies (option 1 of the user menu)
5. View showtimes for a movie+date (option 2)
6. Seat map for a showtime, showing AVAILABLE/HELD/BOOKED (option 3, before selecting seats)
7. Successful seat hold with booking reference (option 3, after entering seat IDs)
8. Confirming a held reservation (option 5)
9. Viewing "My reservations" showing a CONFIRMED booking (option 4)
10. A rejected booking attempt: seat already held/booked
    (repeat option 3 for a seat you already confirmed)
11. Cancelling an eligible reservation (option 6)
12. Login as `admin`
13. Admin menu
14. Creating a showtime successfully (option 5)
15. A rejected showtime creation: overlapping schedule conflict
    (repeat option 5 with an overlapping time window)
16. Admin reports output (option 7): summary, revenue by movie, screen utilization
17. Terminal output of `./scripts/test.sh` showing 36/36 tests passing, including the
    `ReservationConcurrencyTest` repetitions

None of these are included as image files in this repository -- they must be captured
from an actual run on the evaluator's/student's machine, per the "do not fabricate
screenshots" requirement.
