package com.ramesh.user_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request record for user creation.
 * Includes Jakarta Validation for API-level integrity.
 */
public record UserRequestDTO(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @Email(message = "Invalid email format") @NotBlank String email
) {}
