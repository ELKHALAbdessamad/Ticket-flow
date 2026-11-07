package com.ticketflow.bookingservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketflow.bookingservice.clients.EventServiceClient;
import com.ticketflow.bookingservice.clients.dto.EventServiceEventResponse;
import com.ticketflow.bookingservice.clients.dto.EventServiceTicketTierResponse;
import com.ticketflow.bookingservice.service.BookingExpirationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "ticketflow.booking.hold-duration=PT0.2S",
        "ticketflow.booking.expiration-scan-interval=PT1H"})
@AutoConfigureMockMvc
class BookingApiTest {

    private static final AtomicLong IDS = new AtomicLong(1000);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EventServiceClient events;

    @Autowired
    BookingExpirationService expirationService;

    private long prepareCatalog(int quantity) {
        long eventId = IDS.incrementAndGet();
        long tierId = eventId + 100_000;
        Instant start = Instant.now().plusSeconds(3600);
        when(events.getEvent(eventId)).thenReturn(new EventServiceEventResponse(eventId, "Jazz night",
                start, null, start.plusSeconds(7200), "PUBLISHED", quantity, List.of()));
        when(events.getTicketTier(eventId, tierId)).thenReturn(new EventServiceTicketTierResponse(tierId,
                "Standard", new BigDecimal("125.50"), quantity, Instant.now().minusSeconds(10),
                start.minusSeconds(30), eventId));
        return eventId;
    }

    private String request(long eventId, long tierId, int quantity, long customerId, String key) {
        return """
                {"eventId":%d,"customerId":%d,"lines":[{"ticketTierId":%d,"quantity":%d}],
                 "idempotencyKey":"%s"}
                """.formatted(eventId, customerId, tierId, quantity, key);
    }

    private String key() {
        return "booking-" + UUID.randomUUID();
    }

    private String create(long eventId, long tierId, int quantity, long customerId, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(eventId, tierId, quantity, customerId, key)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void createsSnapshotsIdempotentlyAndReportsPerTierStock() throws Exception {
        long eventId = prepareCatalog(5);
        long tierId = eventId + 100_000;
        String key = key();
        String body = request(eventId, tierId, 2, 42, key);

        String first = mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.lines[0].eventTitle").value("Jazz night"))
                .andExpect(jsonPath("$.lines[0].ticketTierName").value("Standard"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(125.5))
                .andExpect(jsonPath("$.totalAmount").value(251.0))
                .andReturn().getResponse().getContentAsString();

        String originalReference = com.jayway.jsonpath.JsonPath.read(first, "$.bookingReference");
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingReference").value((Object) originalReference));
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(eventId, tierId, 3, 42, key)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/bookings"));
        mockMvc.perform(get("/api/v1/inventory/events/{eventId}/ticket-tiers/{tierId}", eventId, tierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(5))
                .andExpect(jsonPath("$.reservedQuantity").value(2))
                .andExpect(jsonPath("$.availableQuantity").value(3));
    }

    @Test
    void confirmationCancellationAndPaymentFailureMoveInventoryOnce() throws Exception {
        long eventId = prepareCatalog(4);
        long tierId = eventId + 100_000;
        String created = create(eventId, tierId, 2, 55, key());
        int id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/v1/internal/bookings/{id}/confirm", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").exists());
        mockMvc.perform(patch("/api/v1/internal/bookings/{id}/confirm", id))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/inventory/events/{eventId}/ticket-tiers/{tierId}", eventId, tierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andExpect(jsonPath("$.soldQuantity").value(2));

        long event2 = prepareCatalog(4);
        long tier2 = event2 + 100_000;
        int failedId = com.jayway.jsonpath.JsonPath.read(create(event2, tier2, 1, 56, key()), "$.id");
        mockMvc.perform(patch("/api/v1/internal/bookings/{id}/payment-failed", failedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        long event3 = prepareCatalog(2);
        long tier3 = event3 + 100_000;
        int cancelledId = com.jayway.jsonpath.JsonPath.read(create(event3, tier3, 2, 57, key()), "$.id");
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", cancelledId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(patch("/api/v1/bookings/{id}/cancel", cancelledId))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredReservationsReleaseTheirStockAndCannotBeConfirmed() throws Exception {
        long eventId = prepareCatalog(2);
        long tierId = eventId + 100_000;
        int id = com.jayway.jsonpath.JsonPath.read(create(eventId, tierId, 1, 77, key()), "$.id");
        Thread.sleep(300);

        assertThat(expirationService.expire((long) id)).isTrue();
        mockMvc.perform(patch("/api/v1/internal/bookings/{id}/confirm", id))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/bookings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mockMvc.perform(get("/api/v1/inventory/events/{eventId}/ticket-tiers/{tierId}", eventId, tierId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andExpect(jsonPath("$.availableQuantity").value(2));
    }

    @Test
    void rejectsUnpublishedEventsAndUnavailableTicketTiers() throws Exception {
        long eventId = IDS.incrementAndGet();
        long tierId = eventId + 100_000;
        Instant start = Instant.now().plusSeconds(3600);
        when(events.getEvent(eventId)).thenReturn(new EventServiceEventResponse(eventId, "Draft event",
                start, start, start.plusSeconds(7200), "DRAFT", 10, List.of()));
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(eventId, tierId, 1, 90, key())))
                .andExpect(status().isConflict());

        long otherEvent = prepareCatalog(10);
        long otherTier = otherEvent + 100_000;
        when(events.getTicketTier(otherEvent, otherTier)).thenReturn(new EventServiceTicketTierResponse(
                otherTier, "Standard", BigDecimal.TEN, 10, Instant.now().plusSeconds(600),
                start.minusSeconds(30), otherEvent));
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content(request(otherEvent, otherTier, 1, 90, key())))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentRequestsCannotOversellAndReadRoutesReturnSnapshots() throws Exception {
        int capacity = 3;
        long eventId = prepareCatalog(capacity);
        long tierId = eventId + 100_000;
        var executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> attempts = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            long customerId = 1000 + i;
            attempts.add(executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                                .content(request(eventId, tierId, 1, customerId, key())))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (Future<Integer> attempt : attempts) {
            int status = attempt.get();
            assertThat(status).isIn(201, 409);
            if (status == 201) {
                created++;
            }
        }
        executor.shutdown();
        assertThat(created).isEqualTo(capacity);

        mockMvc.perform(get("/api/v1/bookings/customer/{customerId}", 1000)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservedQuantity").value(3))
                .andExpect(jsonPath("$[0].availableQuantity").value(0));
    }
}
