package com.ticketflow.bookingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketflow.bookingservice.clients.EventClient;
import com.ticketflow.bookingservice.clients.EventView;
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

/**
 * A REAL HTTP exchange: a tiny fake event-service listens on a random port and the real Feign client
 * calls it (no mock). It checks the contract (path and JSON), the 404 handling and, above all,
 * that a slow service is cut off by the read timeout instead of freezing booking-service.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.openfeign.client.config.default.read-timeout=500"})
@AutoConfigureMockMvc
class EventClientContractTest {

    private static final HttpServer EVENT_SERVICE = startFakeEventService();

    @Autowired
    EventClient client;

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void eventServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("ticketflow.clients.event-service.url",
                () -> "http://localhost:" + EVENT_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeEventService() {
        EVENT_SERVICE.stop(0);
    }

    // ---- the fake event-service ----

    private static HttpServer startFakeEventService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool()); // a slow answer must not block the others
            server.createContext("/api/events/", EventClientContractTest::handle);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String id = exchange.getRequestURI().getPath().substring("/api/events/".length());
        switch (id) {
            case "1" -> reply(exchange, 200, event(1, "PUBLISHED"));
            case "2" -> reply(exchange, 404, "{\"title\":\"Not Found\",\"status\":404}");
            case "3" -> { // answers far too late
                sleep(2000);
                reply(exchange, 200, event(3, "PUBLISHED"));
            }
            case "4" -> reply(exchange, 500, "{\"title\":\"Internal Server Error\",\"status\":500}");
            case "5" -> reply(exchange, 200, event(5, "PUBLISHED"));
            default -> reply(exchange, 404, "{}");
        }
    }

    /** The same JSON shape as event-service's EventResponse, with the fields booking does not use. */
    private static String event(long id, String status) {
        return """
                {"id":%d,"title":"Jazz night","description":"Live","venue":"Casablanca",
                 "startsAt":"%s","price":150.00,"capacity":10,"status":"%s","createdAt":"%s"}
                """.formatted(id, Instant.now().plus(30, ChronoUnit.DAYS), status, Instant.now());
    }

    private static void reply(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException clientGaveUp) {
            // the caller timed out and closed the connection: expected in the slow-service test
        } finally {
            exchange.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- the contract ----

    @Test
    void readsAnEventOfAnotherServiceOverHttp() {
        EventView event = client.get(1L);

        assertThat(event.id()).isEqualTo(1L);
        assertThat(event.title()).isEqualTo("Jazz night");
        assertThat(event.status()).isEqualTo("PUBLISHED");
        assertThat(event.capacity()).isEqualTo(10);
        assertThat(event.price()).isEqualByComparingTo("150.00");
        assertThat(event.startsAt()).isAfter(Instant.now());
    }

    @Test
    void anUnknownEventBecomesAFeign404() {
        assertThatThrownBy(() -> client.get(2L))
                .isInstanceOfSatisfying(FeignException.class, e -> assertThat(e.status()).isEqualTo(404));
    }

    @Test
    void aSlowServiceIsCutOffByTheReadTimeout() {
        long begin = System.nanoTime();

        assertThatThrownBy(() -> client.get(3L)).isInstanceOf(FeignException.class);

        long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;
        assertThat(elapsedMillis).as("the call must stop around the 500 ms timeout, not wait for the 2 s answer")
                .isLessThan(1500);
    }

    // ---- through the whole service ----

    @Test
    void bookingWorksEndToEndWithTheRealClient() throws Exception {
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":5,\"customerId\":42,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventTitle").value("Jazz night"))
                .andExpect(jsonPath("$.totalPrice").value(300.0));
    }

    @Test
    void aFailingOrSlowEventServiceGives503AndAMissingOneGives404() throws Exception {
        for (int broken : new int[] {3, 4}) { // 3 = too slow, 4 = HTTP 500
            mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventId\":" + broken + ",\"customerId\":42,\"quantity\":1}"))
                    .andExpect(status().isServiceUnavailable());
        }
        mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2,\"customerId\":42,\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }
}
