-- Provas importadas via PDF
CREATE TABLE IF NOT EXISTS exam_templates (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(500) NOT NULL,
    contest_id      BIGINT REFERENCES contests(id),
    year            INTEGER,
    role            VARCHAR(255),
    total_questions INTEGER DEFAULT 0,
    status          VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    source_file     VARCHAR(500),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Questões extraídas das provas
CREATE TABLE IF NOT EXISTS exam_template_questions (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT NOT NULL REFERENCES exam_templates(id) ON DELETE CASCADE,
    question_id     BIGINT REFERENCES questions(id),
    original_number INTEGER,
    original_text   TEXT NOT NULL,
    original_answer BOOLEAN,
    topic_suggested VARCHAR(255),
    discipline_suggested VARCHAR(255),
    imported        BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_etq_template
    ON exam_template_questions(template_id);
CREATE INDEX IF NOT EXISTS idx_et_contest
    ON exam_templates(contest_id);