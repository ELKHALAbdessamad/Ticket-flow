package com.ticketflow.eventservice.dto;

import com.ticketflow.eventservice.entities.Event;
import com.ticketflow.eventservice.entities.EventStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        String venue,
        Instant startsAt,
        BigDecimal price,
        int capacity,
        EventStatus status,
        Instant createdAt) {

    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getVenue(),
                e.getStartsAt(), e.getPrice(), e.getCapacity(), e.getStatus(), e.getCreatedAt());
    }
}
