package com.ticketflow.bookingservice.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * What booking-service needs to know about an event: its OWN small view of event-service's contract.
 * The other fields of the response (venue, description...) are ignored, so event-service can add
 * fields without breaking this service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventView(Long id, String title, Instant startsAt, BigDecimal price, int capacity, String status) {
}
