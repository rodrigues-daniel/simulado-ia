-- Status de revisão para questões geradas por IA
ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS ia_reviewed   BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ia_approved   BOOLEAN DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS ia_confidence NUMERIC(4,3) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS review_note   TEXT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at   TIMESTAMP DEFAULT NULL;

-- Histórico de gerações por tópico
CREATE TABLE IF NOT EXISTS ia_generation_history (
    id              BIGSERIAL PRIMARY KEY,
    topic_id        BIGINT NOT NULL REFERENCES topics(id),
    questions_count INTEGER NOT NULL DEFAULT 0,
    rag_chunks_used INTEGER NOT NULL DEFAULT 0,
    rag_avg_score   NUMERIC(4,3),
    model_used      VARCHAR(100) DEFAULT 'llama3.2:3b',
    status          VARCHAR(30)  DEFAULT 'COMPLETED',
    generated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Métricas do banco vetorial por tópico
CREATE TABLE IF NOT EXISTS rag_quality_metrics (
    id                  BIGSERIAL PRIMARY KEY,
    topic_id            BIGINT REFERENCES topics(id),
    total_chunks        INTEGER DEFAULT 0,
    total_tokens        INTEGER DEFAULT 0,
    avg_chunk_size      NUMERIC(8,2) DEFAULT 0,
    coverage_score      NUMERIC(4,3) DEFAULT 0,
    last_evaluated      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_questions_source
    ON questions(source) WHERE source = 'IA-GERADA';

CREATE INDEX IF NOT EXISTS idx_questions_ia_reviewed
    ON questions(ia_reviewed, ia_approved);