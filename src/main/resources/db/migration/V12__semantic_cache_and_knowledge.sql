-- Habilita extensão vetorial (idempotente)
CREATE EXTENSION IF NOT EXISTS vector;

-- Tabela de conhecimento estruturado (RAG principal)
CREATE TABLE IF NOT EXISTS conhecimento_estudo (
    id          BIGSERIAL PRIMARY KEY,
    conteudo    TEXT NOT NULL,
    materia     VARCHAR(255),
    topico_id   BIGINT REFERENCES topics(id) ON DELETE SET NULL,
    contest_id  BIGINT REFERENCES contests(id) ON DELETE SET NULL,
    fonte       VARCHAR(500),
    embedding   vector(768),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tabela de cache semântico de respostas IA
CREATE TABLE IF NOT EXISTS cache_questoes (
    id           BIGSERIAL PRIMARY KEY,
    pergunta     TEXT NOT NULL,
    resposta     TEXT NOT NULL,
    contexto     TEXT,
    embedding    vector(768),
    hits         INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    last_hit_at  TIMESTAMP
);

-- Índice HNSW para busca vetorial rápida
CREATE INDEX IF NOT EXISTS idx_conhecimento_embedding
    ON conhecimento_estudo
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_cache_questoes_embedding
    ON cache_questoes
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Índices auxiliares
CREATE INDEX IF NOT EXISTS idx_conhecimento_materia
    ON conhecimento_estudo(materia);

CREATE INDEX IF NOT EXISTS idx_conhecimento_topico
    ON conhecimento_estudo(topico_id);

CREATE INDEX IF NOT EXISTS idx_conhecimento_contest
    ON conhecimento_estudo(contest_id);