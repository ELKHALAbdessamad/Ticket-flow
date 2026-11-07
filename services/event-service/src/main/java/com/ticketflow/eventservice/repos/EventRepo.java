package com.ticketflow.eventservice.repos;

import com.ticketflow.eventservice.entities.Event;
import com.ticketflow.eventservice.entities.EventStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepo extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    @EntityGraph(attributePaths = {"venue", "category", "ticketTiers"})
    Optional<Event> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"venue", "category", "ticketTiers"})
    Optional<Event> findWithDetailsByIdAndStatus(Long id, EventStatus status);

    @EntityGraph(attributePaths = {"venue", "category"})
    Page<Event> findAll(Specification<Event> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"venue", "category", "ticketTiers"})
    List<Event> findWithDetailsByIdIn(Collection<Long> ids);

    boolean existsByVenueId(Long venueId);
    boolean existsByCategoryId(Long categoryId);

    @Query("select coalesce(max(event.capacity), 0) from Event event where event.venue.id = :venueId")
    int findMaximumCapacityForVenue(@Param("venueId") Long venueId);

    long countByIdAndStatus(Long id, EventStatus status);
}
