CREATE TABLE IF NOT EXISTS rag_document_cleaning (
    document_id      BIGINT PRIMARY KEY REFERENCES rag_documents(id)
                         ON DELETE CASCADE,
    cleaned_content  TEXT NOT NULL,
    strategy_used    VARCHAR(50) NOT NULL,
    confidence_score NUMERIC(4,3) NOT NULL DEFAULT 0,
    manually_edited  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Adicione ao V11__cleaning_cache.sql ou crie V12
ALTER TABLE rag_documents
    ADD COLUMN IF NOT EXISTS cleaning_confidence NUMERIC(4,3) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS cleaning_strategy   VARCHAR(50)  DEFAULT NULL;

-- Comentário para rastreabilidade
COMMENT ON COLUMN rag_documents.status IS
    'QUEUED | PROCESSING | COMPLETED | ERROR | AWAITING_REVIEW';