package com.ramesh.notification_service.repository;

import com.ramesh.notification_service.entity.DeadLetterEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    /** Dead-letter events still awaiting the self-healing reprocessor (SLI #11: DLT depth). */
    long countByReprocessedFalse();

    /**
     * Reprocessing candidates: not yet reprocessed and still under the poison-message
     * cap. Oldest-first so the backlog drains in failure order.
     */
    List<DeadLetterEvent> findByReprocessedFalseAndReprocessAttemptsLessThanOrderByFailedAtAsc(
            int maxAttempts, Pageable pageable);
}