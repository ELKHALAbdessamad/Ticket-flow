package com.ticketflow.eventservice.exceptions;

/** Raised when an operation is not allowed in the current state of the event (HTTP 409). */
public class InvalidEventStateException extends RuntimeException {

    public InvalidEventStateException(String message) {
        super(message);
    }
}
