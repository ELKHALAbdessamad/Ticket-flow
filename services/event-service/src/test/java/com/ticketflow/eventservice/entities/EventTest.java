package com.ticketflow.eventservice.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.eventservice.exceptions.InvalidEventStateException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/** Pure unit tests of the event life cycle: no Spring, no database. */
class EventTest {

    private static Event newEvent(int capacity) {
        return new Event("Concert", "Live", "Casablanca",
                Instant.now().plus(30, ChronoUnit.DAYS), new BigDecimal("150.00"), capacity);
    }

    private static void rename(Event event, int capacity) {
        event.update("New title", "Live", "Rabat",
                Instant.now().plus(40, ChronoUnit.DAYS), new BigDecimal("200.00"), capacity);
    }

    @Test
    void newEventIsADraft() {
        assertThat(newEvent(100).getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void publishThenClose() {
        Event event = newEvent(100);

        event.publish();
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);

        event.close();
        assertThat(event.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    void cannotPublishTwice() {
        Event event = newEvent(100);
        event.publish();

        assertThatThrownBy(event::publish).isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void cannotCloseADraft() {
        assertThatThrownBy(() -> newEvent(100).close()).isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void aClosedEventCannotBeModified() {
        Event event = newEvent(100);
        event.publish();
        event.close();

        assertThatThrownBy(() -> rename(event, 100)).isInstanceOf(InvalidEventStateException.class);
    }

    @Test
    void aDraftCapacityCanBeLowered() {
        Event event = newEvent(100);

        rename(event, 50);

        assertThat(event.getCapacity()).isEqualTo(50);
    }

    @Test
    void aPublishedCapacityCanGrowButNotShrink() {
        Event event = newEvent(100);
        event.publish();

        rename(event, 150);
        assertThat(event.getCapacity()).isEqualTo(150);

        assertThatThrownBy(() -> rename(event, 120)).isInstanceOf(InvalidEventStateException.class);
        assertThat(event.getCapacity()).isEqualTo(150);
    }
}
