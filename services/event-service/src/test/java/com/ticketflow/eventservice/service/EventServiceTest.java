package com.ticketflow.eventservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.entities.Event;
import com.ticketflow.eventservice.entities.EventStatus;
import com.ticketflow.eventservice.exceptions.EventNotFoundException;
import com.ticketflow.eventservice.repos.EventRepo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    EventRepo repo;

    @InjectMocks
    EventService service;

    private final EventRequest request = new EventRequest("Concert", "Live", "Casablanca",
            Instant.now().plus(30, ChronoUnit.DAYS), new BigDecimal("150.00"), 100);

    @Test
    void createSavesADraft() {
        when(repo.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event saved = service.create(request);

        assertThat(saved.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(saved.getTitle()).isEqualTo("Concert");
        verify(repo).save(any(Event.class));
    }

    @Test
    void getThrowsWhenTheEventDoesNotExist() {
        when(repo.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void publishMovesTheEventToPublished() {
        Event event = new Event("Concert", "Live", "Casablanca",
                Instant.now().plus(30, ChronoUnit.DAYS), new BigDecimal("150.00"), 100);
        when(repo.findById(1L)).thenReturn(Optional.of(event));

        assertThat(service.publish(1L).getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void listWithoutVenueReturnsAllPublishedEvents() {
        when(repo.findByStatusOrderByStartsAtAsc(EventStatus.PUBLISHED)).thenReturn(List.of());

        assertThat(service.listPublished(null)).isEmpty();
        assertThat(service.listPublished("  ")).isEmpty();
        verify(repo, org.mockito.Mockito.times(2)).findByStatusOrderByStartsAtAsc(EventStatus.PUBLISHED);
    }

    @Test
    void listWithVenueFiltersOnTheTrimmedVenue() {
        when(repo.findByStatusAndVenueIgnoreCaseOrderByStartsAtAsc(EventStatus.PUBLISHED, "Rabat"))
                .thenReturn(List.of());

        assertThat(service.listPublished("  Rabat ")).isEmpty();
        verify(repo).findByStatusAndVenueIgnoreCaseOrderByStartsAtAsc(EventStatus.PUBLISHED, "Rabat");
    }
}
