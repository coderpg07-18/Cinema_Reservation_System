package com.cinemareserve.domain;

public class Seat {
    private Long id;
    private Long screenId;
    private String seatRow;
    private int seatNumber;
    private SeatType seatType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScreenId() { return screenId; }
    public void setScreenId(Long screenId) { this.screenId = screenId; }
    public String getSeatRow() { return seatRow; }
    public void setSeatRow(String seatRow) { this.seatRow = seatRow; }
    public int getSeatNumber() { return seatNumber; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }
    public SeatType getSeatType() { return seatType; }
    public void setSeatType(SeatType seatType) { this.seatType = seatType; }

    public String getLabel() {
        return seatRow + seatNumber;
    }
}
