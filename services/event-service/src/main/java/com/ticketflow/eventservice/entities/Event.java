package com.ticketflow.eventservice.entities;

import com.ticketflow.eventservice.exceptions.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private Instant startDateTime;

    @Column(nullable = false)
    private Instant endDateTime;

    @Column(length = 2048)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketTier> ticketTiers = new ArrayList<>();

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Event() {
    }

    public Event(String title, String description, Instant startDateTime, Instant endDateTime,
                 String imageUrl, Venue venue, Category category, int capacity) {
        this.title = title;
        this.description = description;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.imageUrl = imageUrl;
        this.venue = venue;
        this.category = category;
        this.capacity = capacity;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String title, String description, Instant startDateTime, Instant endDateTime,
                       String imageUrl, Venue venue, Category category, int capacity) {
        requireDraft("Only draft events can be modified");
        this.title = title;
        this.description = description;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.imageUrl = imageUrl;
        this.venue = venue;
        this.category = category;
        this.capacity = capacity;
        this.updatedAt = Instant.now();
    }

    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new BusinessException("Only a draft event can be published");
        }
        status = EventStatus.PUBLISHED;
        updatedAt = Instant.now();
    }

    public void close() {
        if (status != EventStatus.PUBLISHED) {
            throw new BusinessException("Only a published event can be closed");
        }
        status = EventStatus.CLOSED;
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (status != EventStatus.DRAFT && status != EventStatus.PUBLISHED) {
            throw new BusinessException("Only a draft or published event can be cancelled");
        }
        status = EventStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public void requireDraft(String message) {
        if (status != EventStatus.DRAFT) {
            throw new BusinessException(message);
        }
    }

    public void addTicketTier(TicketTier ticketTier) {
        ticketTiers.add(ticketTier);
    }

    public void removeTicketTier(TicketTier ticketTier) {
        ticketTiers.remove(ticketTier);
    }

    public void refreshUpdatedAt() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartDateTime() { return startDateTime; }
    public Instant getEndDateTime() { return endDateTime; }
    public String getImageUrl() { return imageUrl; }
    public EventStatus getStatus() { return status; }
    public Venue getVenue() { return venue; }
    public Category getCategory() { return category; }
    public List<TicketTier> getTicketTiers() { return ticketTiers; }
    public int getCapacity() { return capacity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
