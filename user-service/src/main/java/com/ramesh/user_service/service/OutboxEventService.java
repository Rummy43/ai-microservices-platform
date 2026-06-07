package com.ramesh.user_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.common.CorrelationConstants;
import com.ramesh.user_service.entity.OutboxEvent;
import com.ramesh.user_service.enums.OutboxEventStatus;
import com.ramesh.user_service.identity.IdentityContext;
import com.ramesh.user_service.identity.IdentityContextHolder;
import com.ramesh.user_service.outbox.payload.UserCreatedOutboxPayload;
import com.ramesh.user_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void saveUserCreatedEvent(UserCreatedEvent event) {
        try {
            UserCreatedOutboxPayload payload = new UserCreatedOutboxPayload(
                    event.getEventId().toString(),
                    event.getId().toString(),
                    event.getFirstName().toString(),
                    event.getLastName().toString(),
                    event.getEmail().toString()
            );

            // Capture audit/trace context on the request thread; the scheduled
            // publisher runs on a different thread where this is no longer available.
            IdentityContext actor = IdentityContextHolder.get().orElse(null);
            String traceId = MDC.get(CorrelationConstants.TRACE_ID);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(UUID.fromString(event.getEventId().toString()))
                    .aggregateId(UUID.fromString(event.getId().toString()))
                    .aggregateType("USER")
                    .eventType("USER_CREATED")
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .actorUsername(actor != null ? actor.username() : null)
                    .actorEmail(actor != null ? actor.email() : null)
                    .actorRoles(actor != null ? actor.rolesAsString() : null)
                    .traceId(traceId)
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize UserCreatedEvent for outbox", ex);
        }
    }
}