package com.ticketflow.bookingservice.controllers;

import com.ticketflow.bookingservice.dto.request.CreateBookingRequest;
import com.ticketflow.bookingservice.dto.response.BookingResponse;
import com.ticketflow.bookingservice.dto.response.PageResponse;
import com.ticketflow.bookingservice.service.BookingCancellationService;
import com.ticketflow.bookingservice.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Validated
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Temporary ticket reservations and customer booking history")
public class BookingController {

    private final BookingService bookingService;
    private final BookingCancellationService cancellationService;

    public BookingController(BookingService bookingService, BookingCancellationService cancellationService) {
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
    }

    @PostMapping
    @Operation(summary = "Create a temporary booking",
            description = "Checks the published event and ticket tiers, atomically holds inventory, and returns a "
                    + "PENDING_PAYMENT booking with a configured expiry. Retry with the same customer and "
                    + "idempotencyKey to receive the original booking.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, content = @Content(schema = @Schema(implementation = CreateBookingRequest.class),
                    examples = @ExampleObject(value = """
                            {"eventId":12,"customerId":42,"lines":[{"ticketTierId":31,"quantity":2}],
                             "idempotencyKey":"checkout-42-a81c4f"}
                            """))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Temporary booking created"),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "404", description = "Event or ticket tier not found"),
                    @ApiResponse(responseCode = "409", description = "Inventory, eligibility or idempotency conflict"),
                    @ApiResponse(responseCode = "503", description = "Event Service unavailable")
            })
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse created = BookingResponse.from(bookingService.create(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking by ID", description = "Returns booking and immutable price snapshots.")
    @ApiResponse(responseCode = "200", description = "Booking found")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    public BookingResponse get(@PathVariable @Positive Long id) {
        return BookingResponse.from(bookingService.get(id));
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Get a booking by its public reference")
    @ApiResponse(responseCode = "200", description = "Booking found")
    @ApiResponse(responseCode = "404", description = "Booking reference not found")
    public BookingResponse getByReference(@PathVariable String reference) {
        return BookingResponse.from(bookingService.getByReference(reference));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "List a customer's bookings",
            description = "Development endpoint. Replace customerId with the authenticated principal when security is enabled.")
    public PageResponse<BookingResponse> listForCustomer(
            @PathVariable @Positive Long customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(bookingService.listForCustomer(customerId, page, size).map(BookingResponse::from));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending booking",
            description = "Pending bookings are cancelled and their held inventory is released atomically. "
                    + "Confirmed bookings are not refundable through this API.")
    @ApiResponse(responseCode = "200", description = "Booking cancelled")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    @ApiResponse(responseCode = "409", description = "Booking is not pending payment")
    public BookingResponse cancel(@PathVariable @Positive Long id) {
        return BookingResponse.from(cancellationService.cancel(id));
    }
}
