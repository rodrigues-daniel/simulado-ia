-- Remove dependência da tabela vector_store do Spring AI
-- O sistema agora usa conhecimento_estudo para tudo

-- Opcional: migra dados existentes do vector_store para conhecimento_estudo
-- (rode só se tiver dados em vector_store que queira preservar)
INSERT INTO conhecimento_estudo (conteudo, fonte, embedding)
SELECT
    content,
    metadata::json->>'source',
    embedding
FROM vector_store
WHERE embedding IS NOT NULL
ON CONFLICT DO NOTHING;

-- Pode dropar vector_store se quiser limpar
-- DROP TABLE IF EXISTS vector_store;
-- (comentado por segurança — rode manualmente quando confirmar que tudo funciona)