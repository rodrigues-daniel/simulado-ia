-- Anotações do usuário por questão
CREATE TABLE IF NOT EXISTS question_notes (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    note        TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Questões salvas/favoritas pelo usuário
CREATE TABLE IF NOT EXISTS saved_questions (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    saved_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_saved_question UNIQUE (question_id)
);

-- Cache de explicações da IA por questão
CREATE TABLE IF NOT EXISTS ia_explanation_cache (
    id              BIGSERIAL PRIMARY KEY,
    question_id     BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    user_answer     BOOLEAN NOT NULL,
    explanation     TEXT NOT NULL,
    rag_source      BOOLEAN NOT NULL DEFAULT FALSE,
    rag_avg_score   NUMERIC(4,3),
    model_used      VARCHAR(100) DEFAULT 'llama3.2:3b',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ia_explanation_cache UNIQUE (question_id, user_answer)
);

CREATE INDEX IF NOT EXISTS idx_question_notes_question
    ON question_notes(question_id);

CREATE INDEX IF NOT EXISTS idx_saved_questions_question
    ON saved_questions(question_id);

CREATE INDEX IF NOT EXISTS idx_ia_explanation_cache_question
    ON ia_explanation_cache(question_id, user_answer);