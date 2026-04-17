package com.ramesh.notification_service.dto;

public record UserCreatedEvent(
        String userId,
        String email,
        String name
) {}