-- Templates de geração de provas
CREATE TABLE IF NOT EXISTS exam_gen_templates (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(500) NOT NULL,
    description     TEXT,
    contest_id      BIGINT REFERENCES contests(id),
    total_questions INTEGER NOT NULL DEFAULT 20,
    time_limit_min  INTEGER NOT NULL DEFAULT 90,
    discipline_config JSONB NOT NULL DEFAULT '[]',
    difficulty_dist   JSONB NOT NULL DEFAULT
        '{"FACIL":30,"MEDIO":50,"DIFICIL":20}',
    style_notes     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Provas geradas a partir dos templates
CREATE TABLE IF NOT EXISTS generated_exams (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES exam_gen_templates(id),
    name            VARCHAR(500) NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'GENERATING',
    total_questions INTEGER NOT NULL DEFAULT 0,
    generation_model VARCHAR(100) DEFAULT 'llama3.2:3b',
    rag_used        BOOLEAN DEFAULT FALSE,
    cache_used      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Questões das provas geradas (com gabarito)
CREATE TABLE IF NOT EXISTS generated_exam_questions (
    id              BIGSERIAL PRIMARY KEY,
    exam_id         BIGINT NOT NULL REFERENCES generated_exams(id)
                        ON DELETE CASCADE,
    question_id     BIGINT REFERENCES questions(id),
    order_number    INTEGER NOT NULL,
    statement       TEXT NOT NULL,
    correct_answer  BOOLEAN NOT NULL,
    explanation     TEXT,
    discipline      VARCHAR(255),
    topic           VARCHAR(255),
    difficulty      VARCHAR(20) DEFAULT 'MEDIO',
    law_reference   VARCHAR(500),
    trap_keywords   TEXT[] DEFAULT '{}',
    rag_score       NUMERIC(4,3),
    from_cache      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Gabaritos exportáveis
CREATE TABLE IF NOT EXISTS generated_exam_answers (
    id              BIGSERIAL PRIMARY KEY,
    exam_id         BIGINT NOT NULL REFERENCES generated_exams(id)
                        ON DELETE CASCADE,
    question_order  INTEGER NOT NULL,
    correct_answer  BOOLEAN NOT NULL,
    UNIQUE (exam_id, question_order)
);

CREATE INDEX IF NOT EXISTS idx_geq_exam
    ON generated_exam_questions(exam_id, order_number);
CREATE INDEX IF NOT EXISTS idx_ge_template
    ON generated_exams(template_id);
CREATE INDEX IF NOT EXISTS idx_egt_contest
    ON exam_gen_templates(contest_id);