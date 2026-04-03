-- Análise Pareto por tópico e concurso
CREATE TABLE pareto_analysis (
    id              BIGSERIAL PRIMARY KEY,
    topic_id        BIGINT NOT NULL REFERENCES topics(id),
    contest_id      BIGINT REFERENCES contests(id),
    incidence_rate  NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    question_count  INTEGER NOT NULL DEFAULT 0,
    avg_difficulty  NUMERIC(3,2) DEFAULT 0.0,
    is_pareto_top   BOOLEAN NOT NULL DEFAULT FALSE,
    last_updated    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Performance do usuário por tópico
CREATE TABLE user_topic_performance (
    id              BIGSERIAL PRIMARY KEY,
    topic_id        BIGINT NOT NULL REFERENCES topics(id),
    total_answered  INTEGER DEFAULT 0,
    total_correct   INTEGER DEFAULT 0,
    total_wrong     INTEGER DEFAULT 0,
    accuracy_rate   NUMERIC(5,4) DEFAULT 0.0,
    last_studied    TIMESTAMP,
    mastery_level   VARCHAR(20) DEFAULT 'INICIANTE',
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Palavras-chave armadilha do Cebraspe
CREATE TABLE trap_keywords (
    id          BIGSERIAL PRIMARY KEY,
    keyword     VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    hit_count   INTEGER DEFAULT 0,
    category    VARCHAR(50) DEFAULT 'ABSOLUTO'
);

-- Performance por palavra-chave armadilha
CREATE TABLE user_keyword_performance (
    id              BIGSERIAL PRIMARY KEY,
    keyword_id      BIGINT NOT NULL REFERENCES trap_keywords(id),
    wrong_count     INTEGER DEFAULT 0,
    total_seen      INTEGER DEFAULT 0,
    error_rate      NUMERIC(5,4) DEFAULT 0.0,
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed: palavras-chave clássicas do Cebraspe
INSERT INTO trap_keywords (keyword, description, category) VALUES
('sempre',          'Termo absoluto que raramente é verdadeiro em lei', 'ABSOLUTO'),
('nunca',           'Termo absoluto de negação total', 'ABSOLUTO'),
('exclusivamente',  'Restrição total que costuma ter exceções', 'ABSOLUTO'),
('somente',         'Restrição que pode ter exceções legais', 'ABSOLUTO'),
('apenas',          'Limitador frequentemente incorreto', 'ABSOLUTO'),
('obrigatoriamente','Pode haver exceções na norma', 'ABSOLUTO'),
('qualquer',        'Generalização ampla que costuma ter restrições', 'GENERALIZACAO'),
('todo',            'Generalização que pode ter exceções', 'GENERALIZACAO'),
('nenhum',          'Negação absoluta rara no direito', 'ABSOLUTO'),
('é vedado',        'Proibição que pode ter exceções expressas', 'PROIBICAO'),
('independentemente','Independência que pode ter condições', 'ABSOLUTO'),
('automaticamente', 'Efeito imediato que pode exigir ato formal', 'PROCEDIMENTO');

CREATE INDEX idx_pareto_topic ON pareto_analysis(topic_id);
CREATE INDEX idx_pareto_top ON pareto_analysis(is_pareto_top);
CREATE INDEX idx_user_performance_topic ON user_topic_performance(topic_id);