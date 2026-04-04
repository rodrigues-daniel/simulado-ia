-- Configuração global de chunking
CREATE TABLE IF NOT EXISTS system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT NOT NULL,
    description TEXT,
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO system_config (key, value, description) VALUES
('rag.chunk.size.kb',     '50',  'Tamanho máximo de cada chunk em KB'),
('rag.chunk.overlap.pct', '10',  'Percentual de sobreposição entre chunks'),
('rag.max.files',         '5',   'Máximo de arquivos por upload')
ON CONFLICT (key) DO NOTHING;

-- Fila de processamento RAG
CREATE TABLE IF NOT EXISTS rag_processing_queue (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
    file_name     VARCHAR(500) NOT NULL,
    chunk_index   INTEGER NOT NULL DEFAULT 0,
    total_chunks  INTEGER NOT NULL DEFAULT 1,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rag_queue_status
    ON rag_processing_queue(status, created_at);

CREATE INDEX IF NOT EXISTS idx_rag_queue_document
    ON rag_processing_queue(document_id);