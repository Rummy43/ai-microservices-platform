package com.ramesh.notification_service.repository;

import com.ramesh.notification_service.entity.DeadLetterEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    /** Dead-letter events still awaiting the self-healing reprocessor (SLI #11: DLT depth). */
    long countByReprocessedFalse();
}