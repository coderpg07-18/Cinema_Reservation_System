package com.cinemareserve.domain;

import java.time.LocalDateTime;

public class Showtime {
    private Long id;
    private Long movieId;
    private String movieTitle; // convenience on read-joins
    private Long screenId;
    private String screenName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double basePrice;
    private ShowtimeStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }
    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }
    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }
    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
    public ShowtimeStatus getStatus() { return status; }
    public void setStatus(ShowtimeStatus status) { this.status = status; }
}
