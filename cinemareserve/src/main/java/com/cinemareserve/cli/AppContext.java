package com.cinemareserve.cli;

import com.cinemareserve.dao.*;
import com.cinemareserve.service.*;

/** Manual wiring of DAOs into services. No DI framework is warranted at this scale --
 *  a single composition root like this is easier to trace during a viva than a
 *  container doing reflection behind the scenes. */
public class AppContext {

    public final AuthService authService;
    public final MovieService movieService;
    public final VenueService venueService;
    public final SchedulingService schedulingService;
    public final ReservationService reservationService;
    public final ReportingService reportingService;

    public AppContext() {
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
    }
}
