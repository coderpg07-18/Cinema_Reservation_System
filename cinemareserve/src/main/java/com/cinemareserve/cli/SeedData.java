package com.cinemareserve.cli;

import com.cinemareserve.dao.GenreDao;
import com.cinemareserve.dao.UserDao;
import com.cinemareserve.db.Database;
import com.cinemareserve.domain.Genre;
import com.cinemareserve.domain.Movie;
import com.cinemareserve.domain.Role;
import com.cinemareserve.domain.Screen;
import com.cinemareserve.domain.Theatre;
import com.cinemareserve.domain.User;
import com.cinemareserve.security.PasswordHasher;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * DEVELOPMENT-ONLY seed data. Not run automatically in production use -- invoked
 * explicitly via `--seed` so a fresh checkout can be demoed immediately. The admin
 * password below is a documented development default (see README "Sample Credentials"),
 * never a real production secret, and the schema stores only its BCrypt hash.
 */
public class SeedData {

    public static void main(String[] args) throws SQLException {
        run();
    }

    public static void run() throws SQLException {
        AppContext ctx = new AppContext();
        UserDao userDao = new UserDao();
        GenreDao genreDao = new GenreDao();

        try (Connection conn = Database.getConnection()) {
            if (userDao.findByUsername(conn, "admin").isPresent()) {
                System.out.println("Seed data already present -- skipping.");
                return;
            }

            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@cinemareserve.local");
            admin.setPasswordHash(PasswordHasher.hash("Admin@123"));
            admin.setRole(Role.ADMIN);
            admin.setActive(true);
            userDao.insert(conn, admin);

            User demoUser = new User();
            demoUser.setUsername("alice");
            demoUser.setEmail("alice@example.com");
            demoUser.setPasswordHash(PasswordHasher.hash("Password1"));
            demoUser.setRole(Role.USER);
            demoUser.setActive(true);
            userDao.insert(conn, demoUser);

            Genre action = genreDao.insert(conn, new Genre(null, "Action"));
            Genre drama = genreDao.insert(conn, new Genre(null, "Drama"));
            genreDao.insert(conn, new Genre(null, "Comedy"));
            genreDao.insert(conn, new Genre(null, "Sci-Fi"));

            System.out.println("Seed complete.");
            System.out.println("  Admin login -> username: admin  password: Admin@123");
            System.out.println("  Demo user   -> username: alice  password: Password1");
            System.out.println("  Genres: Action(" + action.getId() + "), Drama(" + drama.getId() + "), Comedy, Sci-Fi");
        }

        // The remaining catalog (movie, theatre, screen, seats, showtime) is created
        // through the same service layer the CLI menus use, so this seed exercises the
        // exact same validation and business rules a real admin session would.
        try (Connection conn = Database.getConnection()) {
            User admin = userDao.findByUsername(conn, "admin").orElseThrow();
            Genre action = genreDao.findByName(conn, "Action").orElseThrow();

            Movie movie = new Movie();
            movie.setTitle("Skyline Protocol");
            movie.setDescription("An elite team races to stop a satellite hijack before it triggers global blackout.");
            movie.setDurationMins(128);
            movie.setGenreId(action.getId());
            movie.setLanguage("English");
            ctx.movieService.createMovie(admin, movie);

            Theatre theatre = ctx.venueService.createTheatre(admin, "Galaxy Cineplex", "MG Road, Indore");
            Screen screen = ctx.venueService.createScreen(admin, theatre.getId(), "Screen 1", 40);
            ctx.venueService.generateSeatGrid(admin, screen.getId(), 4, 10);

            java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0);
            java.time.LocalDateTime end = start.plusMinutes(movie.getDurationMins() + 15);
            ctx.schedulingService.createShowtime(admin, movie.getId(), screen.getId(), start, end, 220.0);

            System.out.println("Sample catalog created: movie #" + movie.getId() + ", theatre #" + theatre.getId()
                    + ", screen #" + screen.getId() + " with 40 seats, one showtime tomorrow 18:00.");
        }
    }
}
