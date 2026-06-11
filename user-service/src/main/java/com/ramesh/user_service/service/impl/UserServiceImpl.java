package com.ramesh.user_service.service.impl;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.dto.request.UserRequestDTO;
import com.ramesh.user_service.dto.response.UserResponseDTO;
import com.ramesh.user_service.entity.User;
import com.ramesh.user_service.exception.ResourceConflictException;
import com.ramesh.user_service.exception.ResourceNotFoundException;
import com.ramesh.user_service.mapper.UserMapper;
import com.ramesh.user_service.metrics.UserMetricsService;
import com.ramesh.user_service.repository.UserRepository;
import com.ramesh.user_service.service.OutboxEventService;
import com.ramesh.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final OutboxEventService outboxEventService;
    private final UserMetricsService userMetricsService;

    @Override
    @Transactional
    public UserResponseDTO createUser(UserRequestDTO dto) {
        log.info("Creating user | email: {}", dto.email());

        if (userRepository.existsByEmail(dto.email())) {
            log.warn("User creation rejected — email already exists | email: {}", dto.email());
            userMetricsService.incrementFailedDuplicateEmail();
            throw new ResourceConflictException("Email already registered");
        }

        try {
            User user = userMapper.toEntity(dto);
            User savedUser = userRepository.save(user);
            log.info("User persisted successfully | userId: {} | email: {}",
                    savedUser.getId(), savedUser.getEmail());

            UserCreatedEvent event = UserCreatedEvent.newBuilder()
                    .setEventId(UUID.randomUUID().toString())
                    .setId(savedUser.getId().toString())
                    .setEmail(savedUser.getEmail())
                    .setFirstName(savedUser.getFirstName())
                    .setLastName(savedUser.getLastName())
                    .build();

            outboxEventService.saveUserCreatedEvent(event);

            userMetricsService.incrementCreated();
            return userMapper.toResponse(savedUser);
        } catch (RuntimeException e) {
            userMetricsService.incrementFailedError();
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

}
