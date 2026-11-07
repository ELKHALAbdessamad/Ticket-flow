package com.ticketflow.bookingservice.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative HTTP client: Spring generates the implementation. "event-service" is the Eureka name
 * (load-balanced); the optional url overrides it and bypasses Eureka.
 */
@FeignClient(name = "event-service", url = "${ticketflow.clients.event-service.url:}")
public interface EventClient {

    @GetMapping("/api/events/{id}")
    EventView get(@PathVariable("id") Long id);
}
