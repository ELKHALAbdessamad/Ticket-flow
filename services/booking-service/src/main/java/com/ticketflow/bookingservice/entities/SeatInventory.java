package com.ticketflow.bookingservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** How many seats of an event are held. Changed only by the atomic updates of SeatInventoryRepo. */
@Entity
@Table(name = "seat_inventory")
public class SeatInventory {

    @Id
    private Long eventId;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int held;

    protected SeatInventory() { // required by JPA
    }

    public SeatInventory(Long eventId, int capacity) {
        this.eventId = eventId;
        this.capacity = capacity;
        this.held = 0;
    }

    public Long getEventId() { return eventId; }
    public int getCapacity() { return capacity; }
    public int getHeld() { return held; }
}
