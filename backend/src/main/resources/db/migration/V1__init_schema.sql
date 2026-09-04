-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    github_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    avatar_url VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE repositories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_repository_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    full_name VARCHAR(500) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    default_branch VARCHAR(255) NOT NULL DEFAULT 'main',
    description VARCHAR(2000),
    language VARCHAR(100),
    url VARCHAR(1000) NOT NULL,
    indexing_status VARCHAR(50) NOT NULL DEFAULT 'NOT_INDEXED',
    indexing_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_repo_per_user UNIQUE (user_id, github_repository_id)
);

CREATE INDEX idx_repositories_user_id ON repositories(user_id);

-- Embedding dimension: default 768 for nomic-embed-text (Ollama).
-- If you change EMBEDDING_MODEL/EMBEDDING_DIMENSION, update this column's
-- dimension accordingly and re-run indexing (see README "Changing the embedding model").
CREATE TABLE code_chunks (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    file_path VARCHAR(1000) NOT NULL,
    content TEXT NOT NULL,
    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_code_chunks_repository_id ON code_chunks(repository_id);

-- IVFFlat index for approximate cosine similarity search.
-- Requires at least a handful of rows to be useful; harmless when empty/small.
CREATE INDEX idx_code_chunks_embedding_cosine
    ON code_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources VARCHAR(4000),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_repository_id ON chat_messages(repository_id);
