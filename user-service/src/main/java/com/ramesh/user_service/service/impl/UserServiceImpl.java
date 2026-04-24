package com.ramesh.user_service.service.impl;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.dto.request.UserRequestDTO;
import com.ramesh.user_service.dto.response.UserResponseDTO;
import com.ramesh.user_service.entity.User;
import com.ramesh.user_service.exception.ResourceConflictException;
import com.ramesh.user_service.exception.ResourceNotFoundException;
import com.ramesh.user_service.kafka.EventPublisher;
import com.ramesh.user_service.mapper.UserMapper;
import com.ramesh.user_service.repository.UserRepository;
import com.ramesh.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    public UserResponseDTO createUser(UserRequestDTO dto) {
        log.info("Creating user | email: {}", dto.email());

        if (userRepository.existsByEmail(dto.email())) {
            log.warn("User creation rejected — email already exists | email: {}", dto.email());
            throw new ResourceConflictException("Email already registered");
        }

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

        applicationEventPublisher.publishEvent(event);

        return userMapper.toResponse(savedUser);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(UserCreatedEvent event) {
        eventPublisher.publishUserCreatedEvent(event);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
