-- Persist the originating user's identity (propagated via Kafka headers) on each
-- notification log row, giving a durable per-notification audit trail of who
-- triggered the underlying event.
ALTER TABLE notification_log
    ADD COLUMN actor_username VARCHAR(255),
    ADD COLUMN actor_email    VARCHAR(255),
    ADD COLUMN actor_roles    VARCHAR(512);
