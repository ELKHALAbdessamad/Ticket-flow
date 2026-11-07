package com.ticketflow.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketflow.eventservice.dto.request.EventRequest;
import com.ticketflow.eventservice.entities.Category;
import com.ticketflow.eventservice.entities.Event;
import com.ticketflow.eventservice.entities.EventStatus;
import com.ticketflow.eventservice.entities.Venue;
import com.ticketflow.eventservice.exceptions.BusinessException;
import com.ticketflow.eventservice.repos.CategoryRepo;
import com.ticketflow.eventservice.repos.EventRepo;
import com.ticketflow.eventservice.repos.TicketTierRepo;
import com.ticketflow.eventservice.repos.VenueRepo;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    EventRepo eventRepo;
    @Mock
    VenueRepo venueRepo;
    @Mock
    CategoryRepo categoryRepo;
    @Mock
    TicketTierRepo ticketTierRepo;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(eventRepo, venueRepo, categoryRepo, ticketTierRepo);
    }

    @Test
    void createSavesAnEventAsDraft() {
        Venue venue = new Venue("Hall", "1 Main Street", "Rabat", "Morocco", null, 100);
        Category category = new Category("Music", null);
        when(venueRepo.findById(1L)).thenReturn(Optional.of(venue));
        when(categoryRepo.findById(2L)).thenReturn(Optional.of(category));
        when(eventRepo.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant start = Instant.now().plusSeconds(3600);
        EventRequest request = new EventRequest("Concert", "Live", start, start.plusSeconds(3600),
                null, 1L, 2L, 80);

        Event event = service.create(request);

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getCapacity()).isEqualTo(80);
        verify(eventRepo).save(any(Event.class));
    }

    @Test
    void publicationWithoutTicketTiersIsRejected() {
        Event event = new Event("Concert", "Live", Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200), null,
                new Venue("Hall", "1 Main Street", "Rabat", "Morocco", null, 100),
                new Category("Music", null), 80);
        when(eventRepo.findWithDetailsById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publish(1L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one ticket tier");
    }
}
