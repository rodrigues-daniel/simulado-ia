-- Necessário para o ON CONFLICT (topic_id) funcionar no upsert
ALTER TABLE user_topic_performance
    ADD CONSTRAINT uq_user_topic_performance_topic
    UNIQUE (topic_id);

-- Necessário para ON CONFLICT (keyword_id) funcionar futuramente
ALTER TABLE user_keyword_performance
    ADD CONSTRAINT uq_user_keyword_performance_keyword
    UNIQUE (keyword_id);