package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.repos.SeatInventoryRepo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the seat counter of an event the first time it is booked. Its own transaction: if two
 * requests create it at the same time, one fails on the primary key without spoiling the other's work.
 */
@Component
public class SeatInventoryInitializer {

    private final SeatInventoryRepo inventory;

    public SeatInventoryInitializer(SeatInventoryRepo inventory) {
        this.inventory = inventory;
    }

    /** Insert-only: an existing counter (maybe already used by other bookings) is never reset. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(Long eventId, int capacity) {
        inventory.createIfAbsent(eventId, capacity);
    }
}
