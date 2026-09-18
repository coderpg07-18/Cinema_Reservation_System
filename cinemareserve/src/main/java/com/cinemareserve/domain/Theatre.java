package com.cinemareserve.domain;

public class Theatre {
    private Long id;
    private String name;
    private String location;
    private VenueStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public VenueStatus getStatus() { return status; }
    public void setStatus(VenueStatus status) { this.status = status; }
}
