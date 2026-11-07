package com.ticketflow.eventservice.service;

import com.ticketflow.eventservice.dto.request.EventRequest;
import com.ticketflow.eventservice.entities.Category;
import com.ticketflow.eventservice.entities.Event;
import com.ticketflow.eventservice.entities.EventStatus;
import com.ticketflow.eventservice.entities.TicketTier;
import com.ticketflow.eventservice.entities.Venue;
import com.ticketflow.eventservice.exceptions.BusinessException;
import com.ticketflow.eventservice.exceptions.ResourceNotFoundException;
import com.ticketflow.eventservice.repos.CategoryRepo;
import com.ticketflow.eventservice.repos.EventRepo;
import com.ticketflow.eventservice.repos.TicketTierRepo;
import com.ticketflow.eventservice.repos.VenueRepo;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventService {

    private final EventRepo eventRepo;
    private final VenueRepo venueRepo;
    private final CategoryRepo categoryRepo;
    private final TicketTierRepo ticketTierRepo;

    public EventService(EventRepo eventRepo, VenueRepo venueRepo, CategoryRepo categoryRepo,
                        TicketTierRepo ticketTierRepo) {
        this.eventRepo = eventRepo;
        this.venueRepo = venueRepo;
        this.categoryRepo = categoryRepo;
        this.ticketTierRepo = ticketTierRepo;
    }

    public Event create(EventRequest request) {
        validateDates(request.startDateTime(), request.endDateTime());
        Venue venue = getVenue(request.venueId());
        Category category = getCategory(request.categoryId());
        validateCapacity(request.capacity(), venue);
        return eventRepo.save(new Event(request.title().trim(), request.description().trim(),
                request.startDateTime(), request.endDateTime(), request.imageUrl(),
                venue, category, request.capacity()));
    }

    @Transactional(readOnly = true)
    public Event getPublic(Long id) {
        return eventRepo.findWithDetailsByIdAndStatus(id, EventStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published event", id));
    }

    @Transactional(readOnly = true)
    public Event getForManagement(Long id) {
        return eventRepo.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
    }

    @Transactional(readOnly = true)
    public Page<Event> searchPublished(String keyword, Long categoryId, String city, String venueName,
                                       Instant dateFrom, Instant dateTo, BigDecimal minPrice,
                                       BigDecimal maxPrice, Pageable pageable) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom must be before or equal to dateTo");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must be less than or equal to maxPrice");
        }
        Page<Event> page = eventRepo.findAll(publicationFilters(keyword, categoryId, city, venueName,
                dateFrom, dateTo, minPrice, maxPrice), pageable);
        if (page.isEmpty()) {
            return page;
        }
        List<Long> ids = page.getContent().stream().map(Event::getId).toList();
        Map<Long, Event> eventsWithDetails = eventRepo.findWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        return page.map(event -> eventsWithDetails.get(event.getId()));
    }

    private Specification<Event> publicationFilters(String keyword, Long categoryId, String city,
                                                      String venueName, Instant dateFrom, Instant dateTo,
                                                      BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), EventStatus.PUBLISHED));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            if (categoryId != null) {
                predicates.add(builder.equal(root.get("category").get("id"), categoryId));
            }
            if (city != null && !city.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("venue").get("city")),
                        "%" + city.trim().toLowerCase() + "%"));
            }
            if (venueName != null && !venueName.isBlank()) {
                predicates.add(builder.like(builder.lower(root.get("venue").get("name")),
                        "%" + venueName.trim().toLowerCase() + "%"));
            }
            if (dateFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("endDateTime"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("startDateTime"), dateTo));
            }
            if (minPrice != null || maxPrice != null) {
                var tierQuery = query.subquery(Long.class);
                var tier = tierQuery.from(TicketTier.class);
                List<Predicate> tierPredicates = new ArrayList<>();
                tierPredicates.add(builder.equal(tier.get("event").get("id"), root.get("id")));
                if (minPrice != null) {
                    tierPredicates.add(builder.greaterThanOrEqualTo(tier.get("price"), minPrice));
                }
                if (maxPrice != null) {
                    tierPredicates.add(builder.lessThanOrEqualTo(tier.get("price"), maxPrice));
                }
                tierQuery.select(tier.get("id")).where(tierPredicates.toArray(Predicate[]::new));
                predicates.add(builder.exists(tierQuery));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public Event update(Long id, EventRequest request) {
        validateDates(request.startDateTime(), request.endDateTime());
        Event event = getForManagement(id);
        event.requireDraft("Only draft events can be modified");
        Venue venue = getVenue(request.venueId());
        Category category = getCategory(request.categoryId());
        validateCapacity(request.capacity(), venue);
        long offeredQuantity = event.getTicketTiers().stream().mapToLong(TicketTier::getQuantity).sum();
        if (offeredQuantity > request.capacity()) {
            throw new BusinessException("Event capacity cannot be lower than the ticket quantities offered");
        }
        event.update(request.title().trim(), request.description().trim(), request.startDateTime(),
                request.endDateTime(), request.imageUrl(), venue, category, request.capacity());
        return event;
    }

    public void delete(Long id) {
        Event event = getForManagement(id);
        event.requireDraft("Only draft events can be deleted");
        eventRepo.delete(event);
    }

    public Event publish(Long id) {
        Event event = getForManagement(id);
        validateForPublication(event);
        event.publish();
        return event;
    }

    public Event close(Long id) {
        Event event = getForManagement(id);
        event.close();
        return event;
    }

    public Event cancel(Long id) {
        Event event = getForManagement(id);
        event.cancel();
        return event;
    }

    private void validateForPublication(Event event) {
        validateDates(event.getStartDateTime(), event.getEndDateTime());
        if (event.getStartDateTime().isBefore(Instant.now())) {
            throw new BusinessException("An event with a start date in the past cannot be published");
        }
        List<TicketTier> tiers = event.getTicketTiers();
        if (tiers.isEmpty()) {
            throw new BusinessException("An event must have at least one ticket tier before publication");
        }
        long totalQuantity = 0;
        for (TicketTier tier : tiers) {
            if (tier.getPrice().signum() < 0 || tier.getQuantity() <= 0
                    || !tier.getSalesStartDate().isBefore(tier.getSalesEndDate())
                    || tier.getSalesEndDate().isAfter(event.getStartDateTime())) {
                throw new BusinessException("Every ticket tier must have valid price, quantity and sales dates");
            }
            totalQuantity += tier.getQuantity();
        }
        if (totalQuantity > event.getCapacity()) {
            throw new BusinessException("Ticket quantities cannot exceed the event capacity");
        }
    }

    private void validateDates(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("startDateTime must be before endDateTime");
        }
    }

    private void validateCapacity(int capacity, Venue venue) {
        if (capacity > venue.getCapacity()) {
            throw new BusinessException("Event capacity cannot exceed venue capacity");
        }
    }

    private Venue getVenue(Long id) {
        return venueRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venue", id));
    }

    private Category getCategory(Long id) {
        return categoryRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }
}
