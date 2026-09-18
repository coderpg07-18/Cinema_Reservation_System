package com.cinemareserve;

import com.cinemareserve.dao.*;
import com.cinemareserve.db.Database;
import com.cinemareserve.domain.*;
import com.cinemareserve.security.PasswordHasher;
import com.cinemareserve.service.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Builds a minimal but realistic catalog (admin, user, genre, movie, theatre, screen,
 *  seats, one showtime) so service-layer tests don't each re-derive plumbing data. */
public class TestFixtures {

    public final AuthService authService;
    public final MovieService movieService;
    public final VenueService venueService;
    public final SchedulingService schedulingService;
    public final ReservationService reservationService;
    public final ReportingService reportingService;

    public User admin;
    public User regularUser;
    public Movie movie;
    public Theatre theatre;
    public Screen screen;
    public List<Seat> seats;
    public Showtime showtime;

    public TestFixtures() {
        UserDao userDao = new UserDao();
        GenreDao genreDao = new GenreDao();
        MovieDao movieDao = new MovieDao();
        TheatreDao theatreDao = new TheatreDao();
        ScreenDao screenDao = new ScreenDao();
        SeatDao seatDao = new SeatDao();
        ShowtimeDao showtimeDao = new ShowtimeDao();
        ReservationDao reservationDao = new ReservationDao();

        this.authService = new AuthService(userDao);
        this.movieService = new MovieService(movieDao, genreDao);
        this.venueService = new VenueService(theatreDao, screenDao, seatDao);
        this.schedulingService = new SchedulingService(showtimeDao, movieDao, screenDao);
        this.reservationService = new ReservationService(reservationDao, showtimeDao, seatDao);
        this.reportingService = new ReportingService();

        try (Connection conn = Database.getConnection()) {
            User a = new User();
            a.setUsername("testadmin");
            a.setEmail("testadmin@example.com");
            a.setPasswordHash(PasswordHasher.hash("AdminPass1"));
            a.setRole(Role.ADMIN);
            a.setActive(true);
            this.admin = userDao.insert(conn, a);

            Genre genre = genreDao.insert(conn, new Genre(null, "Action"));

            Movie m = new Movie();
            m.setTitle("Test Movie");
            m.setDescription("A movie used purely for tests.");
            m.setDurationMins(120);
            m.setGenreId(genre.getId());
            m.setLanguage("English");
            this.movie = movieService.createMovie(admin, m);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        this.regularUser = authService.register("testuser", "testuser@example.com", "UserPass1");
        this.theatre = venueService.createTheatre(admin, "Test Theatre", "Test City");
        this.screen = venueService.createScreen(admin, theatre.getId(), "Screen A", 20);
        this.seats = venueService.generateSeatGrid(admin, screen.getId(), 2, 5); // A1-A5, B1-B5
    }

    /** Creates a showtime starting `hoursFromNow` hours out, 2 hours long, assigned to `this.showtime`. */
    public Showtime createShowtime(int hoursFromNow, double basePrice) {
        LocalDateTime start = LocalDateTime.now().plusHours(hoursFromNow);
        LocalDateTime end = start.plusHours(2);
        this.showtime = schedulingService.createShowtime(admin, movie.getId(), screen.getId(), start, end, basePrice);
        return this.showtime;
    }
}
