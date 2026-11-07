package com.ticketflow.eventservice.controllers;

import com.ticketflow.eventservice.dto.request.EventRequest;
import com.ticketflow.eventservice.dto.response.EventResponse;
import com.ticketflow.eventservice.dto.response.PageResponse;
import com.ticketflow.eventservice.service.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/v1")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events")
    public PageResponse<EventResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "startDateTime") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        if (categoryId != null && categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new IllegalArgumentException("Price filters cannot be negative");
        }
        String property = switch (sort) {
            case "title" -> "title";
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            case "startDateTime" -> "startDateTime";
            default -> throw new IllegalArgumentException("Unsupported sort field");
        };
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("direction must be asc or desc");
        }
        var result = eventService.searchPublished(keyword, categoryId, city, venue, dateFrom, dateTo,
                minPrice, maxPrice, PageRequest.of(page, size, Sort.by(sortDirection, property)));
        return PageResponse.from(result.map(EventResponse::from));
    }

    @GetMapping("/events/{id}")
    public EventResponse getPublic(@PathVariable Long id) {
        return EventResponse.from(eventService.getPublic(id));
    }

    @GetMapping("/management/events/{id}")
    public EventResponse getForManagement(@PathVariable Long id) {
        return EventResponse.from(eventService.getForManagement(id));
    }

    @GetMapping("/internal/events/{id}")
    public EventResponse getInternal(@PathVariable Long id) {
        return EventResponse.from(eventService.getForManagement(id));
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        EventResponse created = EventResponse.from(eventService.create(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/events/{id}")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return EventResponse.from(eventService.update(id, request));
    }

    @DeleteMapping("/events/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/events/{id}/publish")
    public EventResponse publish(@PathVariable Long id) {
        return EventResponse.from(eventService.publish(id));
    }

    @PatchMapping("/events/{id}/close")
    public EventResponse close(@PathVariable Long id) {
        return EventResponse.from(eventService.close(id));
    }

    @PatchMapping("/events/{id}/cancel")
    public EventResponse cancel(@PathVariable Long id) {
        return EventResponse.from(eventService.cancel(id));
    }
}
