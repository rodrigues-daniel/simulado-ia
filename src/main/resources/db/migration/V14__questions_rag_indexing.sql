-- Controle de indexação de questões no pipeline RAG
CREATE TABLE IF NOT EXISTS question_rag_index (
    id              BIGSERIAL PRIMARY KEY,
    question_id     BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    indexed_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    content_hash    VARCHAR(64) NOT NULL,  -- SHA-256 do conteúdo indexado
    chunks_created  INTEGER NOT NULL DEFAULT 0,
    knowledge_ids   BIGINT[]  DEFAULT '{}', -- IDs em conhecimento_estudo
    status          VARCHAR(30) NOT NULL DEFAULT 'INDEXED',
    indexed_by      VARCHAR(50) DEFAULT 'manual',
    CONSTRAINT uq_question_rag_index UNIQUE (question_id)
);

-- Índices para consultas frequentes
CREATE INDEX IF NOT EXISTS idx_qri_status
    ON question_rag_index(status);

CREATE INDEX IF NOT EXISTS idx_qri_indexed_at
    ON question_rag_index(indexed_at DESC);

-- View para facilitar consulta de status por tópico
CREATE OR REPLACE VIEW vw_questions_index_status AS
SELECT
    q.id                                AS question_id,
    q.topic_id,
    q.statement,
    q.correct_answer,
    q.source,
    q.created_at                        AS question_created_at,
    q.reviewed_at                       AS question_updated_at,
    qri.id                              AS index_id,
    qri.indexed_at,
    qri.content_hash,
    qri.chunks_created,
    qri.status                          AS index_status,
    CASE
        WHEN qri.id IS NULL
            THEN 'NOT_INDEXED'
        WHEN q.reviewed_at > qri.indexed_at
            OR q.created_at > qri.indexed_at
            THEN 'OUTDATED'
        ELSE 'INDEXED'
    END                                 AS computed_status
FROM questions q
LEFT JOIN question_rag_index qri ON q.id = qri.question_id;