package com.ramesh.user_service.repository;

import com.ramesh.user_service.entity.OutboxEvent;
import com.ramesh.user_service.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
}