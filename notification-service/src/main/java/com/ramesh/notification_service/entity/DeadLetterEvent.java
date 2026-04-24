package com.ramesh.notification_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "reprocessed", nullable = false)
    private boolean reprocessed;

    @Column(name = "reprocessed_at")
    private LocalDateTime reprocessedAt;
}