-- Simulados
CREATE TABLE simulations (
    id              BIGSERIAL PRIMARY KEY,
    contest_id      BIGINT REFERENCES contests(id),
    name            VARCHAR(255) NOT NULL,
    total_questions INTEGER NOT NULL DEFAULT 0,
    time_limit_min  INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Questões do simulado
CREATE TABLE simulation_questions (
    id              BIGSERIAL PRIMARY KEY,
    simulation_id   BIGINT NOT NULL REFERENCES simulations(id),
    question_id     BIGINT NOT NULL REFERENCES questions(id),
    order_number    INTEGER NOT NULL,
    user_answer     BOOLEAN,
    is_skipped      BOOLEAN DEFAULT FALSE,
    answered_at     TIMESTAMP
);

-- Resultado do simulado
CREATE TABLE simulation_results (
    id                      BIGSERIAL PRIMARY KEY,
    simulation_id           BIGINT NOT NULL REFERENCES simulations(id) UNIQUE,
    total_questions         INTEGER NOT NULL,
    answered_count          INTEGER NOT NULL DEFAULT 0,
    correct_count           INTEGER NOT NULL DEFAULT 0,
    wrong_count             INTEGER NOT NULL DEFAULT 0,
    skipped_count           INTEGER NOT NULL DEFAULT 0,
    gross_score             NUMERIC(6,2) DEFAULT 0.0,
    net_score               NUMERIC(6,2) DEFAULT 0.0,
    guessing_tendency_pct   NUMERIC(5,2) DEFAULT 0.0,
    risk_level              VARCHAR(20) DEFAULT 'BAIXO',
    keyword_traps_hit       TEXT[],
    report_json             JSONB,
    calculated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulation_questions_sim ON simulation_questions(simulation_id);
CREATE INDEX idx_simulation_results_sim ON simulation_results(simulation_id);