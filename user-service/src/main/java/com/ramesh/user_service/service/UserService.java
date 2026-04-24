package com.ramesh.user_service.service;

import com.ramesh.user_service.dto.request.UserRequestDTO;
import com.ramesh.user_service.dto.response.UserResponseDTO;

import java.util.UUID;


public interface UserService {
    UserResponseDTO createUser(UserRequestDTO request);
    UserResponseDTO getUserById(UUID id);
}
