package com.ramesh.user_service;

import com.ramesh.events.UserCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

	@MockitoBean
	private KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

	@Test
	void contextLoads() {
	}

}
