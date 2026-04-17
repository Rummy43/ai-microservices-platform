package com.ramesh.user_service.event;

import com.ramesh.user_service.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventPublisher {

    public void publishUserCreatedEvent(User user) {
        log.info("UserCreatedEvent published: {}", user.getId());
    }
}