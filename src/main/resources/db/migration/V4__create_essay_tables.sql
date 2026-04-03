-- Esqueletos de redação discursiva
CREATE TABLE essay_skeletons (
    id              BIGSERIAL PRIMARY KEY,
    topic_id        BIGINT REFERENCES topics(id),
    contest_id      BIGINT REFERENCES contests(id),
    title           VARCHAR(500) NOT NULL,
    introduction    TEXT NOT NULL,
    body_points     JSONB NOT NULL DEFAULT '[]',
    conclusion      TEXT NOT NULL,
    mandatory_keywords TEXT[] NOT NULL DEFAULT '{}',
    banca_tips      TEXT,
    word_limit      INTEGER DEFAULT 30,
    generated_by_ai BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_essay_topic ON essay_skeletons(topic_id);
CREATE INDEX idx_essay_contest ON essay_skeletons(contest_id);