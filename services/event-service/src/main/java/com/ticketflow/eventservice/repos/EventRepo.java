package com.ticketflow.eventservice.repos;

import com.ticketflow.eventservice.entities.Event;
import com.ticketflow.eventservice.entities.EventStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepo extends JpaRepository<Event, Long> {

    List<Event> findByStatusOrderByStartsAtAsc(EventStatus status);

    List<Event> findByStatusAndVenueIgnoreCaseOrderByStartsAtAsc(EventStatus status, String venue);
}
