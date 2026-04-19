package com.ramesh.notification_service.service;

import com.ramesh.events.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    public void handleUserCreated(UserCreatedEvent event) {
        log.info("Sending welcome email to {}", event.getEmail());
    }
}