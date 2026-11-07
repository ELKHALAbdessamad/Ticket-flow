package com.ticketflow.bookingservice.exceptions;

/** Another service did not answer in time or failed: HTTP 503. Resilience patterns come in a later step. */
public class UpstreamUnavailableException extends RuntimeException {
    public UpstreamUnavailableException(String service, Throwable cause) {
        super("The " + service + " is temporarily unavailable, please retry", cause);
    }
}
