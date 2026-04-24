-- Tracks successfully processed Kafka events for idempotency
CREATE TABLE processed_events (
                                  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  event_id            VARCHAR(255) NOT NULL UNIQUE,  -- Dedicated event UUID
                                  event_type          VARCHAR(100) NOT NULL,          -- e.g. USER_CREATED
                                  processed_at        TIMESTAMP NOT NULL DEFAULT now(),
                                  topic               VARCHAR(255) NOT NULL,
                                  partition_number    INT NOT NULL,
                                  offset_number       BIGINT NOT NULL
);

CREATE INDEX idx_processed_events_event_id ON processed_events(event_id);

-- Tracks notifications sent to users
CREATE TABLE notification_log (
                                  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  user_id             VARCHAR(255) NOT NULL,
                                  email               VARCHAR(255) NOT NULL,
                                  notification_type   VARCHAR(100) NOT NULL,   -- WELCOME, PASSWORD_RESET etc
                                  status              VARCHAR(50)  NOT NULL,   -- SENT, FAILED, SKIPPED
                                  attempt_number      INT NOT NULL DEFAULT 1,
                                  error_message       TEXT,
                                  created_at          TIMESTAMP NOT NULL DEFAULT now(),
                                  updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_log_user_id ON notification_log(user_id);
CREATE INDEX idx_notification_log_status  ON notification_log(status);

-- Dead letter events for manual reprocessing
CREATE TABLE dead_letter_events (
                                    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    event_id            VARCHAR(255) NOT NULL,
                                    event_type          VARCHAR(100) NOT NULL,
                                    payload             TEXT NOT NULL,           -- JSON snapshot of event
                                    topic               VARCHAR(255) NOT NULL,
                                    last_error          TEXT,
                                    failed_at           TIMESTAMP NOT NULL DEFAULT now(),
                                    reprocessed         BOOLEAN NOT NULL DEFAULT false,
                                    reprocessed_at      TIMESTAMP
);

CREATE INDEX idx_dead_letter_event_id     ON dead_letter_events(event_id);
CREATE INDEX idx_dead_letter_reprocessed  ON dead_letter_events(reprocessed);