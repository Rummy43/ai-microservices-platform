package com.ramesh.user_service.dto.response;

import java.util.UUID;

/**
 * Response record providing a read-only view of the User.
 */
public record UserResponseDTO(
        UUID id,
        String firstName,
        String lastName,
        String email,
        Boolean isActive
) {}
