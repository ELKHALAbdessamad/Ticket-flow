package com.ticketflow.bookingservice.exceptions;

/** A booking or an event does not exist: HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
