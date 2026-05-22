package com.ramesh.user_service.service;

import com.ramesh.user_service.entity.OutboxEvent;
import com.ramesh.user_service.enums.OutboxEventStatus;
import com.ramesh.user_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventProcessingService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public boolean markAsProcessing(UUID outboxId) {
        int updatedRows = outboxEventRepository.markAsProcessing(
                outboxId,
                OutboxEventStatus.PENDING,
                OutboxEventStatus.PROCESSING,
                LocalDateTime.now()
        );

        return updatedRows == 1;
    }

    @Transactional
    public void saveProcessingResult(OutboxEvent outboxEvent) {
        outboxEventRepository.save(outboxEvent);
    }
}