package com.cinemareserve.cli;

import com.cinemareserve.domain.*;
import com.cinemareserve.exception.*;
import com.cinemareserve.service.ReservationService.SeatAvailabilityView;
import com.cinemareserve.service.ReportingService.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Console entry point. All business logic lives in the service layer; this class is
 * strictly presentation -- read input, call a service, print the result or a friendly
 * error message. Every action is wrapped so a business-rule violation prints a clean
 * message instead of a stack trace, per the "never expose stack traces to normal users"
 * requirement.
 */
public class Main {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Scanner in = new Scanner(System.in);
    private final AppContext ctx = new AppContext();
    private User currentUser;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   CinemaReserve -- Cinema Booking System");
        System.out.println("========================================");
        new Main().run();
    }

    private void run() {
        while (true) {
            if (currentUser == null) {
                if (!showLoginMenu()) return; // user chose exit
            } else if (currentUser.isAdmin()) {
                showAdminMenu();
            } else {
                showUserMenu();
            }
        }
    }

    // ---------------------------------------------------------------- login / register

    private boolean showLoginMenu() {
        System.out.println("\n--- 1) Login  2) Register  0) Exit ---");
        switch (prompt("Choose")) {
            case "1" -> doLogin();
            case "2" -> doRegister();
            case "0" -> { return false; }
            default -> System.out.println("Invalid choice.");
        }
        return true;
    }

    private void doLogin() {
        String username = prompt("Username");
        String password = prompt("Password");
        act(() -> {
            currentUser = ctx.authService.login(username, password);
            System.out.println("Welcome, " + currentUser.getUsername() + " (" + currentUser.getRole() + ")");
        });
    }

    private void doRegister() {
        String username = prompt("Choose a username");
        String email = prompt("Email");
        String password = prompt("Choose a password (min 8 chars)");
        act(() -> {
            User u = ctx.authService.register(username, email, password);
            System.out.println("Account created for " + u.getUsername() + ". Please log in.");
        });
    }

    // ---------------------------------------------------------------------- user menu

    private void showUserMenu() {
        System.out.println("\n--- Logged in as " + currentUser.getUsername() + " ---");
        System.out.println("1) Browse/search movies  2) View showtimes for a movie  3) Book seats");
        System.out.println("4) My reservations  5) Confirm a held reservation  6) Cancel a reservation");
        System.out.println("0) Logout");
        switch (prompt("Choose")) {
            case "1" -> browseMovies();
            case "2" -> viewShowtimes();
            case "3" -> bookSeats();
            case "4" -> myReservations();
            case "5" -> confirmReservation();
            case "6" -> cancelReservation();
            case "0" -> currentUser = null;
            default -> System.out.println("Invalid choice.");
        }
    }

    private void browseMovies() {
        String titleLike = prompt("Title contains (blank for all)");
        act(() -> {
            List<Movie> movies = ctx.movieService.search(titleLike.isBlank() ? null : titleLike, null, null);
            if (movies.isEmpty()) {
                System.out.println("No movies found.");
            }
            for (Movie m : movies) {
                System.out.printf("  [%d] %-30s %-12s %3dmin  %s%n",
                        m.getId(), m.getTitle(), m.getGenreName(), m.getDurationMins(), m.getLanguage());
            }
        });
    }

    private void viewShowtimes() {
        long movieId = promptLong("Movie ID");
        String dateStr = prompt("Date (yyyy-MM-dd)");
        act(() -> {
            LocalDate date = LocalDate.parse(dateStr);
            List<Showtime> shows = ctx.schedulingService.findByMovieAndDate(movieId, date);
            if (shows.isEmpty()) {
                System.out.println("No showtimes for that movie/date.");
            }
            for (Showtime s : shows) {
                System.out.printf("  [%d] %s | screen %s | %s - %s | price from %.2f%n",
                        s.getId(), s.getMovieTitle(), s.getScreenName(),
                        s.getStartTime().format(DT), s.getEndTime().format(DT), s.getBasePrice());
            }
        });
    }

    private void bookSeats() {
        long showtimeId = promptLong("Showtime ID");
        act(() -> {
            List<SeatAvailabilityView> seatMap = ctx.reservationService.getSeatMap(showtimeId);
            System.out.println("Seat map (A=available, H=held, B=booked):");
            for (SeatAvailabilityView v : seatMap) {
                char code = switch (v.availability()) {
                    case AVAILABLE -> 'A';
                    case HELD -> 'H';
                    case BOOKED -> 'B';
                };
                System.out.printf("  %-4s [%s] type=%s%n", v.seat().getLabel(), code, v.seat().getSeatType());
            }
            String seatIdsRaw = prompt("Seat IDs to hold, comma-separated");
            List<Long> seatIds = java.util.Arrays.stream(seatIdsRaw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();
            Reservation r = ctx.reservationService.holdSeats(currentUser, showtimeId, seatIds);
            System.out.printf("Held! Booking reference %s, total %.2f, expires %s. Confirm soon via menu option 5.%n",
                    r.getBookingReference(), r.getTotalAmount(), r.getHoldExpiresAt().format(DT));
        });
    }

    private void myReservations() {
        act(() -> {
            List<Reservation> reservations = ctx.reservationService.listForUser(currentUser);
            if (reservations.isEmpty()) {
                System.out.println("You have no reservations yet.");
            }
            for (Reservation r : reservations) {
                System.out.printf("  [%d] %s | %s | total %.2f | seats: %s%n",
                        r.getId(), r.getBookingReference(), r.getStatus(), r.getTotalAmount(),
                        r.getSeats().stream().map(ReservedSeat::getSeatLabel).toList());
            }
        });
    }

    private void confirmReservation() {
        long id = promptLong("Reservation ID to confirm");
        act(() -> {
            Reservation r = ctx.reservationService.confirmReservation(currentUser, id);
            System.out.println("Confirmed! Reference " + r.getBookingReference());
        });
    }

    private void cancelReservation() {
        long id = promptLong("Reservation ID to cancel");
        act(() -> {
            Reservation r = ctx.reservationService.cancelReservation(currentUser, id);
            System.out.println("Cancelled reservation " + r.getBookingReference());
        });
    }

    // --------------------------------------------------------------------- admin menu

    private void showAdminMenu() {
        System.out.println("\n--- Admin: " + currentUser.getUsername() + " ---");
        System.out.println("1) Add movie  2) Add theatre  3) Add screen  4) Generate seat grid");
        System.out.println("5) Create showtime  6) List upcoming showtimes  7) Reports");
        System.out.println("0) Logout");
        switch (prompt("Choose")) {
            case "1" -> addMovie();
            case "2" -> addTheatre();
            case "3" -> addScreen();
            case "4" -> generateSeatGrid();
            case "5" -> createShowtime();
            case "6" -> listUpcoming();
            case "7" -> reports();
            case "0" -> currentUser = null;
            default -> System.out.println("Invalid choice.");
        }
    }

    private void addMovie() {
        String title = prompt("Title");
        String description = prompt("Description");
        int duration = (int) promptLong("Duration (minutes)");
        long genreId = promptLong("Genre ID (use 1 for Drama seed data or check DB)");
        String language = prompt("Language");
        act(() -> {
            Movie m = new Movie();
            m.setTitle(title);
            m.setDescription(description);
            m.setDurationMins(duration);
            m.setGenreId(genreId);
            m.setLanguage(language);
            m = ctx.movieService.createMovie(currentUser, m);
            System.out.println("Created movie #" + m.getId());
        });
    }

    private void addTheatre() {
        String name = prompt("Theatre name");
        String location = prompt("Location");
        act(() -> {
            Theatre t = ctx.venueService.createTheatre(currentUser, name, location);
            System.out.println("Created theatre #" + t.getId());
        });
    }

    private void addScreen() {
        long theatreId = promptLong("Theatre ID");
        String name = prompt("Screen name");
        int capacity = (int) promptLong("Capacity");
        act(() -> {
            Screen s = ctx.venueService.createScreen(currentUser, theatreId, name, capacity);
            System.out.println("Created screen #" + s.getId());
        });
    }

    private void generateSeatGrid() {
        long screenId = promptLong("Screen ID");
        int rows = (int) promptLong("Number of rows");
        int perRow = (int) promptLong("Seats per row");
        act(() -> {
            var seats = ctx.venueService.generateSeatGrid(currentUser, screenId, rows, perRow);
            System.out.println("Generated " + seats.size() + " seats.");
        });
    }

    private void createShowtime() {
        long movieId = promptLong("Movie ID");
        long screenId = promptLong("Screen ID");
        String start = prompt("Start time (yyyy-MM-ddTHH:mm)");
        String end = prompt("End time (yyyy-MM-ddTHH:mm)");
        double price = Double.parseDouble(prompt("Base price"));
        act(() -> {
            Showtime s = ctx.schedulingService.createShowtime(currentUser, movieId, screenId,
                    LocalDateTime.parse(start), LocalDateTime.parse(end), price);
            System.out.println("Created showtime #" + s.getId());
        });
    }

    private void listUpcoming() {
        act(() -> {
            for (Showtime s : ctx.schedulingService.listUpcoming()) {
                System.out.printf("  [%d] %s | screen %s | %s - %s%n",
                        s.getId(), s.getMovieTitle(), s.getScreenName(), s.getStartTime().format(DT), s.getEndTime().format(DT));
            }
        });
    }

    private void reports() {
        act(() -> {
            SummaryReport summary = ctx.reportingService.summary(currentUser);
            System.out.printf("Reservations: %d total, %d confirmed, %d cancelled%n",
                    summary.totalReservations(), summary.confirmedReservations(), summary.cancelledReservations());
            System.out.printf("Tickets sold: %d | Revenue: %.2f%n", summary.totalTicketsSold(), summary.totalRevenue());

            System.out.println("Revenue by movie:");
            for (MovieRevenueRow row : ctx.reportingService.revenueByMovie(currentUser)) {
                System.out.printf("  %-30s tickets=%-4d revenue=%.2f%n", row.title(), row.ticketsSold(), row.revenue());
            }

            System.out.println("Screen utilization:");
            for (ScreenUtilizationRow row : ctx.reportingService.screenUtilization(currentUser)) {
                System.out.printf("  %s / %-10s seats=%-4d sold=%-4d occupancy=%.1f%%%n",
                        row.theatreName(), row.screenName(), row.totalSeats(), row.ticketsSold(), row.occupancyPercent());
            }
        });
    }

    // ------------------------------------------------------------------------ helpers

    private interface Action {
        void run() throws Exception;
    }

    /** Central error handling: business exceptions print a clean one-line message,
     *  anything unexpected prints a generic message -- no stack trace ever reaches the user. */
    private void act(Action action) {
        try {
            action.run();
        } catch (ValidationException | AuthenticationException | AuthorizationException
                 | NotFoundException | SeatUnavailableException | ScheduleConflictException
                 | InvalidReservationStateException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.out.println("Error: please enter a valid number.");
        } catch (java.time.format.DateTimeParseException e) {
            System.out.println("Error: please enter date/time in the format shown.");
        } catch (Exception e) {
            System.out.println("Unexpected error occurred. Please try again.");
        }
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return in.nextLine().trim();
    }

    private long promptLong(String label) {
        return Long.parseLong(prompt(label));
    }
}
