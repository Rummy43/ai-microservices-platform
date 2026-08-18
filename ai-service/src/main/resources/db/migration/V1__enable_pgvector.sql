-- pgvector extension and vector_store table
-- Spring AI PGVector store is configured with initialize-schema=false; Flyway owns the DDL.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;

CREATE TABLE IF NOT EXISTS vector_store (
    id       UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    content  TEXT,
    metadata JSON,
    embedding vector(768)
);

CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);
