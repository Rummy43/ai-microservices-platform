package com.ramesh.user_service.repository;

import com.ramesh.user_service.entity.OutboxEvent;
import com.ramesh.user_service.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    @Modifying
    @Query("""
    UPDATE OutboxEvent o
    SET o.status = :processingStatus,
        o.processingStartedAt = :processingStartedAt
    WHERE o.id = :id
      AND o.status = :pendingStatus
""")
    int markAsProcessing(
            UUID id,
            OutboxEventStatus pendingStatus,
            OutboxEventStatus processingStatus,
            LocalDateTime processingStartedAt
    );

    long countByStatus(OutboxEventStatus status);

    /** Timestamp of the oldest event in the given status (SLI #7: outbox backlog age). Empty when none. */
    @Query("SELECT MIN(o.createdAt) FROM OutboxEvent o WHERE o.status = :status")
    Optional<LocalDateTime> findOldestCreatedAtByStatus(OutboxEventStatus status);
}