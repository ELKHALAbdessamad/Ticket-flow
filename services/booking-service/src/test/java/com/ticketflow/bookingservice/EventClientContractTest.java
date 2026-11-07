package com.ticketflow.bookingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketflow.bookingservice.clients.EventServiceClient;
import com.ticketflow.bookingservice.clients.dto.EventServiceEventResponse;
import feign.FeignException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"eureka.client.enabled=false", "ticketflow.booking.expiration-scan-interval=PT1H"})
@AutoConfigureMockMvc
class EventClientContractTest {

    private static final HttpServer CATALOG = startCatalog();

    @Autowired
    EventServiceClient client;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void eventServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("ticketflow.clients.event-service.url",
                () -> "http://localhost:" + CATALOG.getAddress().getPort());
    }

    @AfterAll
    static void stopCatalog() {
        CATALOG.stop(0);
    }

    private static HttpServer startCatalog() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/api/v1/internal/events/", EventClientContractTest::handleEvent);
            server.createContext("/api/v1/internal/events/12/ticket-tiers/", EventClientContractTest::handleTier);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void handleEvent(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/999")) {
            reply(exchange, 404, "{\"status\":404,\"detail\":\"not found\"}");
            return;
        }
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        reply(exchange, 200, """
                {"id":12,"title":"Jazz night","startsAt":"%s","startDateTime":"%s",
                 "endDateTime":"%s","status":"PUBLISHED","capacity":100,"ticketTiers":[]}
                """.formatted(start, start, start.plusSeconds(7200)));
    }

    private static void handleTier(HttpExchange exchange) throws IOException {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        reply(exchange, 200, """
                {"id":31,"name":"Standard","price":150.00,"quantity":100,
                 "salesStartDate":"%s","salesEndDate":"%s","eventId":12}
                """.formatted(Instant.now().minus(1, ChronoUnit.DAYS), start.minusSeconds(30)));
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void fetchesTheInternalEventAndTicketTierContractsAndBooksOverHttp() throws Exception {
        EventServiceEventResponse event = client.getEvent(12L);
        assertThat(event.status()).isEqualTo("PUBLISHED");
        assertThat(event.title()).isEqualTo("Jazz night");
        assertThat(event.eventStart()).isAfter(Instant.now());

        assertThat(client.getTicketTier(12L, 31L).price()).isEqualByComparingTo("150.00");

        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":12,"customerId":42,"lines":[{"ticketTierId":31,"quantity":2}],
                                 "idempotencyKey":"http-contract-20261107"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].eventTitle").value("Jazz night"))
                .andExpect(jsonPath("$.lines[0].ticketTierName").value("Standard"))
                .andExpect(jsonPath("$.totalAmount").value(300.0));
    }

    @Test
    void missingEventsAreNotTreatedAsValidCatalogData() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getEvent(999L))
                .isInstanceOf(FeignException.NotFound.class);
    }
}
