package com.ramesh.user_service.dto.response;

/**
 * Response record providing a read-only view of the User.
 */
public record UserResponseDTO(
        String id,
        String firstName,
        String lastName,
        String email,
        Boolean isActive
) {}
