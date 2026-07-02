-- Reprocessing bookkeeping for the self-healing DLT reprocessor: a poison-message
-- cap (reprocess_attempts) and a timestamp to gate exponential backoff between
-- replay attempts. Existing rows default to 0 attempts so they are picked up on the
-- next reprocess cycle.
ALTER TABLE dead_letter_events
    ADD COLUMN reprocess_attempts        INT       NOT NULL DEFAULT 0,
    ADD COLUMN last_reprocess_attempt_at TIMESTAMP;