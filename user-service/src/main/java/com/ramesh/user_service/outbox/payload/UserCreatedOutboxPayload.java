package com.ramesh.user_service.outbox.payload;

public record UserCreatedOutboxPayload(
        String eventId,
        String id,
        String firstName,
        String lastName,
        String email
) {
}