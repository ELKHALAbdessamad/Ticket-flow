package com.ticketflow.eventservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "eureka.client.enabled=false")
class EventServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
