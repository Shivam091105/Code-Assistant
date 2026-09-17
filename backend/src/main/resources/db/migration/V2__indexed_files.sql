-- Tracks the last-indexed GitHub blob SHA for each file we've chunked and
-- embedded, per repository. This is what makes indexing incremental:
-- on the next indexing run we compare each file's current blob SHA
-- (from the GitHub tree API) against what's stored here, and only
-- re-fetch/re-chunk/re-embed files whose SHA changed, plus remove chunks
-- for files that disappeared from the tree.
CREATE TABLE indexed_files (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    file_path VARCHAR(1000) NOT NULL,
    blob_sha VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_indexed_file_per_repo UNIQUE (repository_id, file_path)
);

CREATE INDEX idx_indexed_files_repository_id ON indexed_files(repository_id);