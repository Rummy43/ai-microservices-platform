package com.ramesh.notification_service.repository;

import com.ramesh.notification_service.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
}