package com.ticketflow.bookingservice.controllers;

import com.ticketflow.bookingservice.dto.BookingRequest;
import com.ticketflow.bookingservice.dto.BookingResponse;
import com.ticketflow.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        BookingResponse created = BookingResponse.from(
                service.book(request.eventId(), request.customerId(), request.quantity()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** All bookings, newest first. Optional {@code ?customerId=} filter. */
    @GetMapping
    List<BookingResponse> list(@RequestParam(required = false) Long customerId) {
        return service.list(customerId).stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/{id}")
    BookingResponse get(@PathVariable Long id) {
        return BookingResponse.from(service.get(id));
    }

    @PostMapping("/{id}/cancel")
    BookingResponse cancel(@PathVariable Long id) {
        return BookingResponse.from(service.cancel(id));
    }
}
