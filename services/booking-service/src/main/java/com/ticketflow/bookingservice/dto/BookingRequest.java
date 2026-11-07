package com.ticketflow.bookingservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** What the client sends. Until the security step, the customer is given in the request. */
public record BookingRequest(
        @NotNull Long eventId,
        @NotNull @Positive Long customerId,
        @Min(1) @Max(10) int quantity) {
}
