ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS ia_reviewed   BOOLEAN      DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ia_approved   BOOLEAN      DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS ia_confidence NUMERIC(4,3) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS review_note   TEXT         DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at   TIMESTAMP    DEFAULT NULL;

-- Garante que questões não-IA já venham como revisadas
UPDATE questions
SET ia_reviewed = TRUE,
    ia_approved = TRUE
WHERE source != 'IA-GERADA';