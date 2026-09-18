package com.cinemareserve.domain;

public class ReservedSeat {
    private Long id;
    private Long reservationId;
    private Long showtimeId;
    private Long seatId;
    private String seatLabel; // convenience on read-joins
    private double price;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getShowtimeId() { return showtimeId; }
    public void setShowtimeId(Long showtimeId) { this.showtimeId = showtimeId; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public String getSeatLabel() { return seatLabel; }
    public void setSeatLabel(String seatLabel) { this.seatLabel = seatLabel; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
