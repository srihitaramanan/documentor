-- V1__init.sql
-- Initial schema for DocuMentor.
-- See docs/data-model.md for design rationale.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- for gen_random_uuid()

-- =====================================================================
-- USERS
-- =====================================================================
CREATE TABLE users (
                       id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email         VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- =====================================================================
-- DOCUMENTS
-- =====================================================================
CREATE TABLE documents (
                           id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                           filename        VARCHAR(500) NOT NULL,
                           content_hash    VARCHAR(64),                      -- SHA-256 for idempotent uploads
                           file_size_bytes BIGINT NOT NULL,
                           mime_type       VARCHAR(100) NOT NULL,
                           status          VARCHAR(20) NOT NULL,             -- PENDING | PROCESSING | READY | FAILED
                           page_count      INT,
                           error_message   TEXT,
                           created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                           processed_at    TIMESTAMPTZ
);

CREATE INDEX idx_documents_user        ON documents(user_id);
CREATE UNIQUE INDEX idx_documents_user_hash
    ON documents(user_id, content_hash)
    WHERE content_hash IS NOT NULL;

-- =====================================================================
-- CHUNKS  (with embeddings)
-- =====================================================================
-- Note: Ollama's `nomic-embed-text` produces 768-dim vectors.
-- If you switch to OpenAI text-embedding-3-small (1536-dim) later,
-- you'll need a new migration to alter the column.
CREATE TABLE chunks (
                        id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        document_id  UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                        chunk_index  INT NOT NULL,
                        content      TEXT NOT NULL,
                        page_number  INT,
                        token_count  INT,
                        embedding    vector(768),
                        created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunks_document  ON chunks(document_id);

-- HNSW index on embedding column for fast cosine similarity search.
-- This is the index that makes RAG queries fast.
CREATE INDEX idx_chunks_embedding
    ON chunks
    USING hnsw (embedding vector_cosine_ops);

-- =====================================================================
-- CONVERSATIONS
-- =====================================================================
CREATE TABLE conversations (
                               id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               document_id UUID REFERENCES documents(id) ON DELETE SET NULL,
                               title       VARCHAR(255),
                               created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_user ON conversations(user_id);

-- =====================================================================
-- MESSAGES
-- =====================================================================
CREATE TABLE messages (
                          id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                          role             VARCHAR(20) NOT NULL,            -- USER | ASSISTANT
                          content          TEXT NOT NULL,
                          cited_chunk_ids  UUID[],
                          prompt_tokens    INT,
                          completion_tokens INT,
                          model_used       VARCHAR(100),
                          created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation ON messages(conversation_id);