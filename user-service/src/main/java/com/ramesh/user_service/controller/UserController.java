package com.ramesh.user_service.controller;

import com.ramesh.user_service.dto.request.UserRequestDTO;
import com.ramesh.user_service.dto.response.UserResponseDTO;
import com.ramesh.user_service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Operations related to user profiles")
public class UserController {

    private final UserService userService;

    /**
     * Creates a new user in the platform.
     * @param request the user details
     * @return the created user DTO
     */
    @PostMapping
    @Operation(summary = "Create a new user", description = "Stores a user in the database and returns the created record with a unique UUID.")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO request) {
        UserResponseDTO response = userService.createUser(request);
        URI location = URI.create("/api/v1/users/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Retrieves a user by their unique UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }
}