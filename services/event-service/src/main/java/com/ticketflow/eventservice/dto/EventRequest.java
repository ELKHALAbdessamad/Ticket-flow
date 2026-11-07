package com.ticketflow.eventservice.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record EventRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        @NotBlank String venue,
        @NotNull @Future Instant startsAt,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Positive int capacity) {
}
