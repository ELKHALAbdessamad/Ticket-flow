package com.ticketflow.eventservice.entities;

import com.ticketflow.eventservice.exceptions.InvalidEventStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String venue;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** A new event always starts as a DRAFT. */
    public Event(String title, String description, String venue, Instant startsAt,
                 BigDecimal price, int capacity) {
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.startsAt = startsAt;
        this.price = price;
        this.capacity = capacity;
    }

    /**
     * Business rules on update:
     * - a CLOSED event cannot be modified;
     * - once PUBLISHED, the capacity can only grow (seats may already be sold, and the
     *   booking service owns that count).
     */
    public void update(String title, String description, String venue, Instant startsAt,
                       BigDecimal price, int capacity) {
        if (status == EventStatus.CLOSED) {
            throw new InvalidEventStateException("A closed event cannot be modified");
        }
        if (status == EventStatus.PUBLISHED && capacity < this.capacity) {
            throw new InvalidEventStateException(
                    "The capacity of a published event cannot be lowered (" + this.capacity + " -> " + capacity + ")");
        }
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.startsAt = startsAt;
        this.price = price;
        this.capacity = capacity;
    }

    /** DRAFT -> PUBLISHED. */
    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new InvalidEventStateException("Only a DRAFT event can be published, but it is " + status);
        }
        status = EventStatus.PUBLISHED;
    }

    /** PUBLISHED -> CLOSED. */
    public void close() {
        if (status != EventStatus.PUBLISHED) {
            throw new InvalidEventStateException("Only a PUBLISHED event can be closed, but it is " + status);
        }
        status = EventStatus.CLOSED;
    }
}
