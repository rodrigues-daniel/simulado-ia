-- Habilita extensão vetorial
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Concursos de referência
CREATE TABLE contests (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    organ       VARCHAR(255) NOT NULL,
    role        VARCHAR(255) NOT NULL,
    year        INTEGER NOT NULL,
    level       VARCHAR(50) NOT NULL DEFAULT 'MEDIO',
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tópicos de estudo
CREATE TABLE topics (
    id              BIGSERIAL PRIMARY KEY,
    contest_id      BIGINT REFERENCES contests(id),
    name            VARCHAR(255) NOT NULL,
    discipline      VARCHAR(255) NOT NULL,
    law_reference   TEXT,
    incidence_rate  NUMERIC(5,4) DEFAULT 0.0,
    is_priority     BOOLEAN NOT NULL DEFAULT TRUE,
    is_hidden       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Questões
CREATE TABLE questions (
    id              BIGSERIAL PRIMARY KEY,
    topic_id        BIGINT NOT NULL REFERENCES topics(id),
    contest_id      BIGINT REFERENCES contests(id),
    statement       TEXT NOT NULL,
    correct_answer  BOOLEAN NOT NULL,
    law_paragraph   TEXT,
    law_reference   VARCHAR(500),
    explanation     TEXT,
    professor_tip   TEXT,
    trap_keywords   TEXT[],
    year            INTEGER,
    source          VARCHAR(255),
    difficulty      VARCHAR(20) DEFAULT 'MEDIO',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Sessões de estudo
CREATE TABLE study_sessions (
    id              BIGSERIAL PRIMARY KEY,
    topic_id        BIGINT REFERENCES topics(id),
    started_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMP,
    total_questions INTEGER DEFAULT 0,
    correct_count   INTEGER DEFAULT 0,
    wrong_count     INTEGER DEFAULT 0,
    skipped_count   INTEGER DEFAULT 0
);

-- Respostas individuais
CREATE TABLE question_answers (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES study_sessions(id),
    question_id     BIGINT NOT NULL REFERENCES questions(id),
    user_answer     BOOLEAN,
    is_correct      BOOLEAN,
    answered_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    time_spent_ms   INTEGER
);

-- Índices base
CREATE INDEX idx_questions_topic ON questions(topic_id);
CREATE INDEX idx_questions_contest ON questions(contest_id);
CREATE INDEX idx_question_answers_session ON question_answers(session_id);
CREATE INDEX idx_topics_contest ON topics(contest_id);
CREATE INDEX idx_topics_priority ON topics(is_priority, is_hidden);

-- Concurso default: TCU Técnico Administrativo
INSERT INTO contests (name, organ, role, year, level, is_default)
VALUES ('TCU - Técnico Federal de Controle Externo', 'Tribunal de Contas da União',
        'Técnico Administrativo', 2025, 'MEDIO', TRUE);