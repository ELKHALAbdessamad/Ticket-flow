package com.ticketflow.eventservice.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private static String body(String title, Instant startsAt, int capacity) {
        return """
                {"title":"%s","description":"Live","venue":"Casablanca","startsAt":"%s","price":150.00,"capacity":%d}
                """.formatted(title, startsAt, capacity);
    }

    private static String body(String title, int capacity) {
        return body(title, Instant.now().plus(30, ChronoUnit.DAYS), capacity);
    }

    private static String uniqueTitle(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** Creates a DRAFT event and returns its id, read from the Location header. */
    private String createEvent(String title, int capacity) throws Exception {
        String location = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, capacity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void createReturns201WithLocationAndADraft() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueTitle("concert"), 100)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.capacity").value(100));
    }

    @Test
    void rejectsABlankTitleAndAZeroCapacity() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAStartDateInThePast() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueTitle("old"), Instant.now().minus(1, ChronoUnit.DAYS), 100)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theCatalogListsOnlyPublishedEvents() throws Exception {
        String draftTitle = uniqueTitle("draft");
        String publishedTitle = uniqueTitle("published");
        createEvent(draftTitle, 100);
        String publishedId = createEvent(publishedTitle, 100);
        mockMvc.perform(post("/api/events/{id}/publish", publishedId)).andExpect(status().isOk());

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='" + publishedTitle + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.title=='" + draftTitle + "')]").isEmpty());
    }

    @Test
    void publishThenClose() throws Exception {
        String id = createEvent(uniqueTitle("flow"), 100);

        mockMvc.perform(post("/api/events/{id}/publish", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(post("/api/events/{id}/close", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void invalidTransitionsReturn409() throws Exception {
        String id = createEvent(uniqueTitle("transition"), 100);

        // a DRAFT cannot be closed
        mockMvc.perform(post("/api/events/{id}/close", id)).andExpect(status().isConflict());

        mockMvc.perform(post("/api/events/{id}/publish", id)).andExpect(status().isOk());
        // already published
        mockMvc.perform(post("/api/events/{id}/publish", id)).andExpect(status().isConflict());
    }

    @Test
    void theCapacityOfAPublishedEventCanGrowButNotShrink() throws Exception {
        String title = uniqueTitle("capacity");
        String id = createEvent(title, 100);
        mockMvc.perform(post("/api/events/{id}/publish", id)).andExpect(status().isOk());

        mockMvc.perform(put("/api/events/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, 50)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/events/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(title, 150)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(150));
    }

    @Test
    void unknownEventReturns404() throws Exception {
        mockMvc.perform(get("/api/events/{id}", 999999)).andExpect(status().isNotFound());
    }
}
