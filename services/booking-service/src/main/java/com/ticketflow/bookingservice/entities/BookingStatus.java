package com.ticketflow.bookingservice.entities;

/**
 * PENDING: seats are held (the payment arrives in a later step). CANCELLED: seats released.
 * CONFIRMED is reserved for the payment step: nothing sets it yet.
 */
public enum BookingStatus {
    PENDING, CONFIRMED, CANCELLED;

    /** Does a booking in this status hold seats? */
    public boolean holdsSeats() {
        return this != CANCELLED;
    }
}
