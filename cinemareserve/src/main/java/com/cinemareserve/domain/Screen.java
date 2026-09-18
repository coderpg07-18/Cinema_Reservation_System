package com.cinemareserve.domain;

public class Screen {
    private Long id;
    private Long theatreId;
    private String screenName;
    private int capacity;
    private VenueStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTheatreId() { return theatreId; }
    public void setTheatreId(Long theatreId) { this.theatreId = theatreId; }
    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public VenueStatus getStatus() { return status; }
    public void setStatus(VenueStatus status) { this.status = status; }
}
