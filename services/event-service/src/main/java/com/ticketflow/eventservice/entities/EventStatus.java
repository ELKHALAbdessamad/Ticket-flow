package com.ticketflow.eventservice.entities;

/**
 * Life cycle of an event: DRAFT -> PUBLISHED -> CLOSED.
 * Only PUBLISHED events are listed to the public.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CLOSED
}
