package com.cinemareserve.service;

import com.cinemareserve.dao.GenreDao;
import com.cinemareserve.dao.MovieDao;
import com.cinemareserve.db.Database;
import com.cinemareserve.domain.Movie;
import com.cinemareserve.domain.MovieStatus;
import com.cinemareserve.domain.User;
import com.cinemareserve.exception.NotFoundException;
import com.cinemareserve.exception.ValidationException;
import com.cinemareserve.util.Validator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class MovieService {

    private final MovieDao movieDao;
    private final GenreDao genreDao;

    public MovieService(MovieDao movieDao, GenreDao genreDao) {
        this.movieDao = movieDao;
        this.genreDao = genreDao;
    }

    public Movie createMovie(User actor, Movie movie) {
        AuthorizationGuard.requireAdmin(actor);
        Validator.notBlank(movie.getTitle(), "title");
        Validator.positive(movie.getDurationMins(), "duration");
        Validator.notBlank(movie.getLanguage(), "language");
        if (movie.getStatus() == null) {
            movie.setStatus(MovieStatus.ACTIVE);
        }
        try (Connection conn = Database.getConnection()) {
            genreDao.findById(conn, movie.getGenreId())
                    .orElseThrow(() -> new ValidationException("unknown genre id: " + movie.getGenreId()));
            return movieDao.insert(conn, movie);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create movie", e);
        }
    }

    public void updateMovie(User actor, Movie movie) {
        AuthorizationGuard.requireAdmin(actor);
        Validator.notBlank(movie.getTitle(), "title");
        Validator.positive(movie.getDurationMins(), "duration");
        try (Connection conn = Database.getConnection()) {
            movieDao.findById(conn, movie.getId())
                    .orElseThrow(() -> new NotFoundException("movie not found: " + movie.getId()));
            movieDao.update(conn, movie);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to update movie", e);
        }
    }

    /** Deactivates rather than deletes, so historical reservations against this movie remain valid. */
    public void deactivateMovie(User actor, long movieId) {
        AuthorizationGuard.requireAdmin(actor);
        try (Connection conn = Database.getConnection()) {
            movieDao.findById(conn, movieId)
                    .orElseThrow(() -> new NotFoundException("movie not found: " + movieId));
            movieDao.setStatus(conn, movieId, MovieStatus.INACTIVE);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to deactivate movie", e);
        }
    }

    public Movie getMovie(long movieId) {
        try (Connection conn = Database.getConnection()) {
            return movieDao.findById(conn, movieId)
                    .orElseThrow(() -> new NotFoundException("movie not found: " + movieId));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to fetch movie", e);
        }
    }

    public List<Movie> listActiveMovies() {
        try (Connection conn = Database.getConnection()) {
            return movieDao.findAllActive(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to list movies", e);
        }
    }

    public List<Movie> search(String titleLike, Long genreId, String language) {
        try (Connection conn = Database.getConnection()) {
            return movieDao.search(conn, titleLike, genreId, language);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to search movies", e);
        }
    }
}
