-- Documentos ingeridos no RAG
CREATE TABLE rag_documents (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(500) NOT NULL,
    source_type     VARCHAR(50) NOT NULL DEFAULT 'PDF',
    topic_id        BIGINT REFERENCES topics(id),
    contest_id      BIGINT REFERENCES contests(id),
    file_path       TEXT,
    total_chunks    INTEGER DEFAULT 0,
    status          VARCHAR(30) DEFAULT 'PROCESSING',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Chunks vetorizados (PGVector nativo via Spring AI)
-- A tabela vector_store é criada pelo Spring AI automaticamente
-- mas precisamos da tabela de metadados customizada

CREATE TABLE rag_chunks (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES rag_documents(id),
    chunk_index     INTEGER NOT NULL,
    content         TEXT NOT NULL,
    law_paragraph   TEXT,
    page_number     INTEGER,
    vector_id       UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_chunks_document ON rag_chunks(document_id);
CREATE INDEX idx_rag_documents_topic ON rag_documents(topic_id);