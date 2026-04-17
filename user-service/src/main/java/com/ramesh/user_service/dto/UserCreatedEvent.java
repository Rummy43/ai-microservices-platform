package com.ramesh.user_service.dto;

public record UserCreatedEvent(
        String userId,
        String email,
        String name
) {}