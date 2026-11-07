package com.ticketflow.bookingservice.entities;

import com.ticketflow.bookingservice.exceptions.BookingRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(
        name = "uk_booking_reference", columnNames = "booking_reference"))
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 36)
    private String bookingReference;

    @Column(nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BookingStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant confirmedAt;
    private Instant cancelledAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<BookingLine> lines = new ArrayList<>();

    @Version
    private Long version;

    protected Booking() {
    }

    public Booking(Long customerId, String currency, Instant createdAt, Instant expiresAt) {
        this.bookingReference = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.currency = currency;
        this.status = BookingStatus.PENDING_PAYMENT;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
        this.totalAmount = BigDecimal.ZERO;
    }

    public void addLine(Long eventId, Long ticketTierId, String eventTitle, String tierName,
                        BigDecimal unitPrice, int quantity) {
        BookingLine line = new BookingLine(this, eventId, ticketTierId, eventTitle, tierName, unitPrice, quantity);
        lines.add(line);
        totalAmount = totalAmount.add(line.getSubtotal());
    }

    public void confirm(Instant now) {
        requirePending();
        if (!expiresAt.isAfter(now)) {
            throw new BookingRuleException("The reservation has expired");
        }
        status = BookingStatus.CONFIRMED;
        confirmedAt = now;
        updatedAt = now;
    }

    public void expire(Instant now) {
        requirePending();
        if (expiresAt.isAfter(now)) {
            throw new BookingRuleException("The reservation has not expired");
        }
        status = BookingStatus.EXPIRED;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        requirePending();
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        updatedAt = now;
    }

    public void failPayment(Instant now) {
        requirePending();
        status = BookingStatus.PAYMENT_FAILED;
        updatedAt = now;
    }

    private void requirePending() {
        if (status != BookingStatus.PENDING_PAYMENT) {
            throw new BookingRuleException("Only a pending-payment reservation can be transitioned");
        }
    }

    public Long getId() { return id; }
    public String getBookingReference() { return bookingReference; }
    public Long getCustomerId() { return customerId; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public List<BookingLine> getLines() { return lines; }
}
