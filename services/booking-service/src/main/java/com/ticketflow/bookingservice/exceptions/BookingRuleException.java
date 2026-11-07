package com.ticketflow.bookingservice.exceptions;

/** A business rule forbids the action (sold out, event not open, already cancelled...): HTTP 409. */
public class BookingRuleException extends RuntimeException {
    public BookingRuleException(String message) {
        super(message);
    }
}
