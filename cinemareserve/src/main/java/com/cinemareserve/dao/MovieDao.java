package com.cinemareserve.dao;

import com.cinemareserve.db.Database;

import com.cinemareserve.domain.Movie;
import com.cinemareserve.domain.MovieStatus;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MovieDao {

    private static final String SELECT_JOIN =
        "SELECT m.*, g.name AS genre_name FROM movies m JOIN genres g ON g.id = m.genre_id ";

    public Movie insert(Connection conn, Movie movie) throws SQLException {
        String sql = "INSERT INTO movies (title, description, duration_mins, genre_id, language, release_date, poster_ref, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movie.getTitle());
            ps.setString(2, movie.getDescription());
            ps.setInt(3, movie.getDurationMins());
            ps.setLong(4, movie.getGenreId());
            ps.setString(5, movie.getLanguage());
            ps.setString(6, movie.getReleaseDate() == null ? null : movie.getReleaseDate().toString());
            ps.setString(7, movie.getPosterRef());
            ps.setString(8, movie.getStatus().name());
            ps.executeUpdate();
            movie.setId(Database.lastInsertRowId(conn));
        }
        return movie;
    }

    public void update(Connection conn, Movie movie) throws SQLException {
        String sql = "UPDATE movies SET title=?, description=?, duration_mins=?, genre_id=?, language=?, " +
                     "release_date=?, poster_ref=?, status=?, updated_at=datetime('now') WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movie.getTitle());
            ps.setString(2, movie.getDescription());
            ps.setInt(3, movie.getDurationMins());
            ps.setLong(4, movie.getGenreId());
            ps.setString(5, movie.getLanguage());
            ps.setString(6, movie.getReleaseDate() == null ? null : movie.getReleaseDate().toString());
            ps.setString(7, movie.getPosterRef());
            ps.setString(8, movie.getStatus().name());
            ps.setLong(9, movie.getId());
            ps.executeUpdate();
        }
    }

    public void setStatus(Connection conn, long movieId, MovieStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE movies SET status=?, updated_at=datetime('now') WHERE id=?")) {
            ps.setString(1, status.name());
            ps.setLong(2, movieId);
            ps.executeUpdate();
        }
    }

    public Optional<Movie> findById(Connection conn, long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + " WHERE m.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Movie> findAllActive(Connection conn) throws SQLException {
        List<Movie> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + " WHERE m.status = 'ACTIVE' ORDER BY m.title");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    /** Search/filter by optional title fragment, genre id, and language -- any may be null. */
    public List<Movie> search(Connection conn, String titleLike, Long genreId, String language) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_JOIN + " WHERE m.status = 'ACTIVE'");
        List<Object> params = new ArrayList<>();
        if (titleLike != null && !titleLike.isBlank()) {
            sql.append(" AND m.title LIKE ?");
            params.add("%" + titleLike + "%");
        }
        if (genreId != null) {
            sql.append(" AND m.genre_id = ?");
            params.add(genreId);
        }
        if (language != null && !language.isBlank()) {
            sql.append(" AND m.language = ?");
            params.add(language);
        }
        sql.append(" ORDER BY m.title");
        List<Movie> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    private Movie map(ResultSet rs) throws SQLException {
        Movie m = new Movie();
        m.setId(rs.getLong("id"));
        m.setTitle(rs.getString("title"));
        m.setDescription(rs.getString("description"));
        m.setDurationMins(rs.getInt("duration_mins"));
        m.setGenreId(rs.getLong("genre_id"));
        m.setGenreName(rs.getString("genre_name"));
        m.setLanguage(rs.getString("language"));
        String releaseDate = rs.getString("release_date");
        m.setReleaseDate(releaseDate == null ? null : LocalDate.parse(releaseDate));
        m.setPosterRef(rs.getString("poster_ref"));
        m.setStatus(MovieStatus.valueOf(rs.getString("status")));
        m.setCreatedAt(LocalDateTime.parse(rs.getString("created_at").replace(' ', 'T')));
        m.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at").replace(' ', 'T')));
        return m;
    }
}
