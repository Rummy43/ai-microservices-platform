package com.ramesh.notification_service.service;

import com.ramesh.events.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    public void sendWelcomeNotification(UserCreatedEvent event) {
        log.info("Sending welcome notification | userId: {} | email: {}",
                event.getId(), event.getEmail());

        // TODO: place email provider (SendGrid, SES, etc.)
        // emailClient.send(
        //     WelcomeEmail.builder()
        //         .to(event.getEmail())
        //         .firstName(event.getFirstName())
        //         .build()
        // );

        log.info("Welcome notification sent | userId: {} | email: {}",
                event.getId(), event.getEmail());
    }
}