package com.ticketflow.eventservice.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.eventservice.exceptions.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventTest {

    private Event event() {
        return new Event("Concert", "Live event", Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200), null,
                new Venue("Hall", "1 Main Street", "Rabat", "Morocco", null, 100),
                new Category("Music", null), 80);
    }

    @Test
    void startsAsDraftAndCanBeCancelled() {
        Event event = event();

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        event.cancel();
        assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void onlyPublishedEventsCanBeClosedAndClosedEventsCannotBeReopened() {
        Event event = event();

        assertThatThrownBy(event::close).isInstanceOf(BusinessException.class);
        event.publish();
        event.close();
        assertThat(event.getStatus()).isEqualTo(EventStatus.CLOSED);
        assertThatThrownBy(event::publish).isInstanceOf(BusinessException.class);
    }
}
