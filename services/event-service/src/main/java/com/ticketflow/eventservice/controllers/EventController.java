package com.ticketflow.eventservice.controllers;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.dto.EventResponse;
import com.ticketflow.eventservice.service.EventService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        EventResponse created = EventResponse.from(service.create(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable Long id) {
        return EventResponse.from(service.get(id));
    }

    /** Public catalog: PUBLISHED events only. */
    @GetMapping
    List<EventResponse> list(@RequestParam(required = false) String venue) {
        return service.listPublished(venue).stream().map(EventResponse::from).toList();
    }

    @PutMapping("/{id}")
    EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return EventResponse.from(service.update(id, request));
    }

    @PostMapping("/{id}/publish")
    EventResponse publish(@PathVariable Long id) {
        return EventResponse.from(service.publish(id));
    }

    @PostMapping("/{id}/close")
    EventResponse close(@PathVariable Long id) {
        return EventResponse.from(service.close(id));
    }
}
