package com.ramesh.notification_service.repository;

import com.ramesh.notification_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    boolean existsByEventId(String eventId);
}