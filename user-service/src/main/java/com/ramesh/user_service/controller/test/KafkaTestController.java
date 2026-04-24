package com.ramesh.user_service.controller.test;

import com.ramesh.events.UserCreatedEvent;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kafka")
@RequiredArgsConstructor
@Tag(name = "Kafka Events Validation", description = "Operations related to Kafka event validationo")
public class KafkaTestController {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    @PostMapping("/test/publish")
    public void publish(@RequestBody UserCreatedEvent event) {
        try {
            kafkaTemplate.send("user-created-topic", event.getEventId().toString(), event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
