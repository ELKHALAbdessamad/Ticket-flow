package com.ticketflow.bookingservice.service;

import com.ticketflow.bookingservice.clients.dto.EventServiceEventResponse;
import com.ticketflow.bookingservice.clients.dto.EventServiceTicketTierResponse;
import com.ticketflow.bookingservice.dto.request.BookingLineRequest;
import com.ticketflow.bookingservice.dto.request.CreateBookingRequest;
import com.ticketflow.bookingservice.entities.Booking;
import com.ticketflow.bookingservice.entities.Inventory;
import com.ticketflow.bookingservice.exceptions.BookingRuleException;
import com.ticketflow.bookingservice.exceptions.IdempotencyConflictException;
import com.ticketflow.bookingservice.exceptions.ResourceNotFoundException;
import com.ticketflow.bookingservice.repos.BookingRepo;
import com.ticketflow.bookingservice.repos.IdempotencyRecordRepo;
import com.ticketflow.bookingservice.repos.InventoryRepo;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final EventCatalogService catalogService;
    private final BookingCreationTransaction creationTransaction;
    private final BookingRepo bookingRepo;
    private final IdempotencyRecordRepo idempotencyRepo;
    private final InventoryRepo inventoryRepo;
    private final Clock clock;
    private final int maximumQuantity;

    public BookingService(EventCatalogService catalogService, BookingCreationTransaction creationTransaction,
                          BookingRepo bookingRepo, IdempotencyRecordRepo idempotencyRepo,
                          InventoryRepo inventoryRepo, Clock clock,
                          @Value("${ticketflow.booking.maximum-quantity-per-line}") int maximumQuantity) {
        this.catalogService = catalogService;
        this.creationTransaction = creationTransaction;
        this.bookingRepo = bookingRepo;
        this.idempotencyRepo = idempotencyRepo;
        this.inventoryRepo = inventoryRepo;
        this.clock = clock;
        this.maximumQuantity = maximumQuantity;
    }

    public Booking create(CreateBookingRequest request) {
        validateLines(request);
        String hash = requestHash(request);
        var existing = idempotencyRepo.findByCustomerIdAndIdempotencyKey(
                request.customerId(), request.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException();
            }
            return get(existing.get().getBookingId());
        }

        EventServiceEventResponse event = catalogService.getEvent(request.eventId());
        if (!"PUBLISHED".equals(event.status())) {
            throw new BookingRuleException("The event is not published and open for booking");
        }
        Instant now = Instant.now(clock);
        if (event.eventStart() == null || !event.eventStart().isAfter(now)) {
            throw new BookingRuleException("The event has already started or has no valid start time");
        }
        Map<Long, EventServiceTicketTierResponse> tiers = new HashMap<>();
        for (BookingLineRequest line : request.lines()) {
            EventServiceTicketTierResponse tier = catalogService.getTicketTier(request.eventId(), line.ticketTierId());
            validateTier(event, tier, now);
            tiers.put(tier.id(), tier);
        }
        try {
            return creationTransaction.create(request, hash, event, tiers);
        } catch (DataIntegrityViolationException duplicate) {
            var concurrent = idempotencyRepo.findByCustomerIdAndIdempotencyKey(
                    request.customerId(), request.idempotencyKey());
            if (concurrent.isEmpty()) {
                throw duplicate;
            }
            if (!concurrent.get().getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException();
            }
            return get(concurrent.get().getBookingId());
        }
    }

    @Transactional(readOnly = true)
    public Booking get(Long id) {
        return bookingRepo.findWithLinesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Booking getByReference(String reference) {
        return bookingRepo.findByBookingReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking reference " + reference + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<Booking> listForCustomer(Long customerId, int page, int size) {
        Page<Long> ids = bookingRepo.findIdsForCustomer(customerId, PageRequest.of(page, size));
        Map<Long, Booking> bookings = bookingRepo.findWithLinesByIdIn(ids.getContent()).stream()
                .collect(java.util.stream.Collectors.toMap(Booking::getId, booking -> booking));
        return ids.map(bookings::get);
    }

    @Transactional(readOnly = true)
    public List<Inventory> inventoryForEvent(Long eventId) {
        return inventoryRepo.findByEventIdOrderByTicketTierId(eventId);
    }

    @Transactional(readOnly = true)
    public Inventory inventoryForTier(Long eventId, Long tierId) {
        return inventoryRepo.findByEventIdAndTicketTierId(eventId, tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for ticket tier " + tierId + " not found"));
    }

    private void validateLines(CreateBookingRequest request) {
        Set<Long> tierIds = new HashSet<>();
        for (BookingLineRequest line : request.lines()) {
            if (line.quantity() > maximumQuantity) {
                throw new IllegalArgumentException("Quantity per ticket tier cannot exceed " + maximumQuantity);
            }
            if (!tierIds.add(line.ticketTierId())) {
                throw new IllegalArgumentException("Each ticket tier may appear only once in a booking");
            }
        }
    }

    private void validateTier(EventServiceEventResponse event, EventServiceTicketTierResponse tier, Instant now) {
        if (!event.id().equals(tier.eventId())) {
            throw new ResourceNotFoundException("Ticket tier " + tier.id() + " does not belong to the event");
        }
        if (tier.price() == null || tier.price().compareTo(BigDecimal.ZERO) < 0
                || tier.quantity() <= 0 || tier.salesStartDate() == null || tier.salesEndDate() == null
                || !tier.salesStartDate().isBefore(tier.salesEndDate())) {
            throw new BookingRuleException("The ticket tier has invalid price, capacity or sales dates");
        }
        if (now.isBefore(tier.salesStartDate()) || !now.isBefore(tier.salesEndDate())) {
            throw new BookingRuleException("The ticket tier is not currently on sale");
        }
    }

    private String requestHash(CreateBookingRequest request) {
        String canonical = request.eventId() + "|" + request.customerId() + "|"
                + request.lines().stream()
                    .sorted(java.util.Comparator.comparing(BookingLineRequest::ticketTierId))
                    .map(line -> line.ticketTierId() + ":" + line.quantity())
                    .reduce((left, right) -> left + "|" + right).orElse("");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
