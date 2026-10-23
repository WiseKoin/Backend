CREATE TABLE ai_ingestion_requests (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL,
    idempotency_key_hash VARCHAR(64),
    media_fingerprint VARCHAR(64) NOT NULL,
    fingerprint_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    first_extraction_id UUID REFERENCES ai_extractions(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ai_ingestion_idempotency
        UNIQUE (user_id, source, idempotency_key_hash),
    CONSTRAINT uk_ai_ingestion_daily_fingerprint
        UNIQUE (user_id, source, media_fingerprint, fingerprint_date)
);

CREATE INDEX idx_ai_ingestion_created_at
    ON ai_ingestion_requests(created_at);
