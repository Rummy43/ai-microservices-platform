package com.ramesh.user_service.service.impl;

import com.ramesh.user_service.dto.request.UserRequestDTO;
import com.ramesh.user_service.dto.response.UserResponseDTO;
import com.ramesh.user_service.entity.User;
import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.exception.ResourceConflictException;
import com.ramesh.user_service.exception.ResourceNotFoundException;
import com.ramesh.user_service.kafka.EventPublisher;
import com.ramesh.user_service.mapper.UserMapper;
import com.ramesh.user_service.repository.UserRepository;
import com.ramesh.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public UserResponseDTO createUser(UserRequestDTO dto) {
        log.info("Attempting to create user with email: {}", dto.email());

        if (userRepository.existsByEmail(dto.email())) {
            log.warn("User creation failed: Email {} already exists", dto.email());
            throw new ResourceConflictException("Email already registered");
        }

        User user = userMapper.toEntity(dto);

        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());
        UserCreatedEvent event = UserCreatedEvent.newBuilder()
                .setId(savedUser.getId().toString())
                .setEmail(savedUser.getEmail())
                .setFirstName(savedUser.getFirstName())
                .setLastName(savedUser.getLastName())
                .build();
        eventPublisher.publishUserCreatedEvent(event);
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(String id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
