package com.ticketflow.eventservice.service;

import com.ticketflow.eventservice.dto.EventRequest;
import com.ticketflow.eventservice.entities.Event;
import com.ticketflow.eventservice.entities.EventStatus;
import com.ticketflow.eventservice.exceptions.EventNotFoundException;
import com.ticketflow.eventservice.repos.EventRepo;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventService {

    private final EventRepo repo;

    public EventService(EventRepo repo) {
        this.repo = repo;
    }

    public Event create(EventRequest request) {
        return repo.save(new Event(request.title(), request.description(), request.venue(),
                request.startsAt(), request.price(), request.capacity()));
    }

    @Transactional(readOnly = true)
    public Event get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    /** The public catalog: only PUBLISHED events, optionally filtered by venue. */
    @Transactional(readOnly = true)
    public List<Event> listPublished(String venue) {
        if (venue == null || venue.isBlank()) {
            return repo.findByStatusOrderByStartsAtAsc(EventStatus.PUBLISHED);
        }
        return repo.findByStatusAndVenueIgnoreCaseOrderByStartsAtAsc(EventStatus.PUBLISHED, venue.trim());
    }

    public Event update(Long id, EventRequest request) {
        Event event = get(id);
        event.update(request.title(), request.description(), request.venue(),
                request.startsAt(), request.price(), request.capacity());
        return event;
    }

    public Event publish(Long id) {
        Event event = get(id);
        event.publish();
        return event;
    }

    public Event close(Long id) {
        Event event = get(id);
        event.close();
        return event;
    }
}
