-- Creates ai_db for the ai-service PGVector store.
-- PostgreSQL init scripts run only on first container start from an empty volume.
-- For existing volumes: docker exec postgres psql -U postgres -c "CREATE DATABASE ai_db;"
CREATE DATABASE ai_db;
GRANT ALL PRIVILEGES ON DATABASE ai_db TO postgres;
