package com.ticketflow.eventservice.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void managesDraftsAndOnlyPublishesEventsWithValidTicketOffers() throws Exception {
        long venueId = createVenue();
        long categoryId = createCategory();
        String title = "Event-" + UUID.randomUUID();
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        String eventBody = """
                {"title":"%s","description":"A live event","startDateTime":"%s","endDateTime":"%s",
                 "imageUrl":"https://example.test/event.png","venueId":%d,"categoryId":%d,"capacity":100}
                """.formatted(title, start, end, venueId, categoryId);
        String location = mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getHeader("Location");
        String eventId = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/v1/events/" + eventId));
        mockMvc.perform(get("/api/v1/management/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.city").value("Rabat"));
        mockMvc.perform(get("/api/v1/internal/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(patch("/api/v1/events/{id}/publish", eventId))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/events").param("size", "101"))
                .andExpect(status().isBadRequest());

        String tierBody = """
                {"name":"Standard","description":"General admission","price":150.00,"quantity":80,
                 "salesStartDate":"%s","salesEndDate":"%s"}
                """.formatted(Instant.now().minus(1, ChronoUnit.DAYS), start.minus(1, ChronoUnit.DAYS));
        String tierLocation = mockMvc.perform(post("/api/v1/events/{eventId}/ticket-tiers", eventId)
                        .contentType(MediaType.APPLICATION_JSON).content(tierBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String tierId = tierLocation.substring(tierLocation.lastIndexOf('/') + 1);
        mockMvc.perform(get("/api/v1/internal/events/{eventId}/ticket-tiers/{ticketTierId}", eventId, tierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(Integer.parseInt(eventId)))
                .andExpect(jsonPath("$.name").value("Standard"));

        mockMvc.perform(patch("/api/v1/events/{id}/publish", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.ticketTiers.length()").value(1));
        mockMvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title));
        mockMvc.perform(get("/api/v1/events").param("keyword", title).param("city", "rab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(delete("/api/v1/venues/{id}", venueId))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidTierValuesAndInvalidEventDates() throws Exception {
        long venueId = createVenue();
        long categoryId = createCategory();
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String invalidEvent = """
                {"title":"Bad event","description":"Description","startDateTime":"%s","endDateTime":"%s",
                 "venueId":%d,"categoryId":%d,"capacity":20}
                """.formatted(start, start.minus(1, ChronoUnit.DAYS), venueId, categoryId);
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(invalidEvent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    private long createVenue() throws Exception {
        String body = """
                {"name":"Venue-%s","address":"1 Main Street","city":"Rabat","country":"Morocco",
                 "description":"Concert hall","capacity":200}
                """.formatted(UUID.randomUUID());
        String location = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private long createCategory() throws Exception {
        String body = """
                {"name":"Category-%s","description":"Music"}
                """.formatted(UUID.randomUUID());
        String location = mockMvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
}
