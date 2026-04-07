-- Configuração de concurso para score real
ALTER TABLE simulations
    ADD COLUMN IF NOT EXISTS modality        VARCHAR(20) DEFAULT 'AMPLA',
    ADD COLUMN IF NOT EXISTS total_vacancies INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quota_vacancies INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cut_score_ampla NUMERIC(6,2) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS cut_score_quota NUMERIC(6,2) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS points_correct  NUMERIC(4,2) DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS points_wrong    NUMERIC(4,2) DEFAULT 1.0;

-- Histórico de notas de corte por concurso
CREATE TABLE IF NOT EXISTS cut_scores (
    id              BIGSERIAL PRIMARY KEY,
    contest_id      BIGINT NOT NULL REFERENCES contests(id),
    modality        VARCHAR(20) NOT NULL DEFAULT 'AMPLA',
    cut_score       NUMERIC(6,2) NOT NULL,
    year            INTEGER NOT NULL,
    total_vacancies INTEGER DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cut_scores_contest
    ON cut_scores(contest_id, year DESC);