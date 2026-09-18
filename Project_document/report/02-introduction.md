# Introduction

CinemaReserve is a backend cinema reservation system built as a console (CLI)
application in plain Java. It lets customers browse movies, check showtimes, and
book specific seats, while cinema administrators manage the movie/theatre/screen
catalog, schedule showtimes, and review operational reports.

The project's technical emphasis, by design, is not CRUD -- it is the reservation
engine: guaranteeing that a seat can never be sold twice, that a screen can never
host two overlapping shows, and that cancellation follows an explicit, enforced
policy rather than an ad-hoc check.
