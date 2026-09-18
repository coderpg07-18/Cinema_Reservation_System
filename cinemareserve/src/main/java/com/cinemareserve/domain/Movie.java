package com.cinemareserve.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Movie {
    private Long id;
    private String title;
    private String description;
    private int durationMins;
    private Long genreId;
    private String genreName; // convenience field populated on read-joins
    private String language;
    private LocalDate releaseDate;
    private String posterRef;
    private MovieStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDurationMins() { return durationMins; }
    public void setDurationMins(int durationMins) { this.durationMins = durationMins; }
    public Long getGenreId() { return genreId; }
    public void setGenreId(Long genreId) { this.genreId = genreId; }
    public String getGenreName() { return genreName; }
    public void setGenreName(String genreName) { this.genreName = genreName; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public String getPosterRef() { return posterRef; }
    public void setPosterRef(String posterRef) { this.posterRef = posterRef; }
    public MovieStatus getStatus() { return status; }
    public void setStatus(MovieStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
