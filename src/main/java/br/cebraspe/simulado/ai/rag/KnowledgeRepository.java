package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.ai.embedding.EmbeddingService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeRepository {

    private final JdbcClient     jdbcClient;
    private final EmbeddingService embeddingService;

    public KnowledgeRepository(JdbcClient jdbcClient,
                                EmbeddingService embeddingService) {
        this.jdbcClient       = jdbcClient;
        this.embeddingService = embeddingService;
    }

    public record KnowledgeChunk(
            Long   id,
            String conteudo,
            String materia,
            Long   topicoId,
            Long   contestId,
            String fonte,
            LocalDateTime createdAt
    ) {}

    public record SearchResult(
            Long   id,
            String conteudo,
            String materia,
            String fonte,
            double similarity
    ) {}

    // ── Salva chunk com embedding ───────────────────────────────────────
    public Long save(String conteudo, String materia,
                     Long topicoId, Long contestId, String fonte) {
        String vector = embeddingService.embedToString(conteudo);

        return jdbcClient.sql("""
                INSERT INTO conhecimento_estudo
                    (conteudo, materia, topico_id, contest_id, fonte, embedding)
                VALUES
                    (:conteudo, :materia, :topicoId, :contestId,
                     :fonte, :embedding::vector)
                RETURNING id
                """)
                .param("conteudo",   conteudo)
                .param("materia",    materia)
                .param("topicoId",   topicoId)
                .param("contestId",  contestId)
                .param("fonte",      fonte)
                .param("embedding",  vector)
                .query(Long.class)
                .single();
    }

    /**
     * Busca os top-K chunks mais relevantes com filtros opcionais.
     * Combina similaridade vetorial + filtros por matéria/tópico.
     */
    public List<SearchResult> search(String query, int topK,
                                      String materia, Long topicoId) {
        String vector = embeddingService.embedToString(query);

        // Monta filtro dinâmico
        String filter = "";
        if (materia  != null) filter += " AND materia = :materia";
        if (topicoId != null) filter += " AND topico_id = :topicoId";

        var sql = jdbcClient.sql("""
                SELECT id, conteudo, materia, fonte,
                       1 - (embedding <=> :vec::vector) AS similarity
                FROM conhecimento_estudo
                WHERE embedding IS NOT NULL
                """ + filter + """
                ORDER BY embedding <=> :vec::vector
                LIMIT :topK
                """)
                .param("vec",  vector)
                .param("topK", topK);

        if (materia  != null) sql = sql.param("materia",  materia);
        if (topicoId != null) sql = sql.param("topicoId", topicoId);

        return sql.query((rs, n) -> new SearchResult(
                rs.getLong("id"),
                rs.getString("conteudo"),
                rs.getString("materia"),
                rs.getString("fonte"),
                rs.getDouble("similarity")
        )).list();
    }

    // Busca sem filtros
    public List<SearchResult> search(String query, int topK) {
        return search(query, topK, null, null);
    }

    public Optional<KnowledgeChunk> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, conteudo, materia, topico_id, contest_id,
                       fonte, created_at
                FROM conhecimento_estudo WHERE id = :id
                """)
                .param("id", id)
                .query(KnowledgeChunk.class)
                .optional();
    }

    public List<KnowledgeChunk> findByTopico(Long topicoId) {
        return jdbcClient.sql("""
                SELECT id, conteudo, materia, topico_id, contest_id,
                       fonte, created_at
                FROM conhecimento_estudo
                WHERE topico_id = :topicoId
                ORDER BY created_at DESC
                """)
                .param("topicoId", topicoId)
                .query(KnowledgeChunk.class)
                .list();
    }

    public void deleteById(Long id) {
        jdbcClient.sql("DELETE FROM conhecimento_estudo WHERE id = :id")
                .param("id", id).update();
    }

    public long count() {
        return jdbcClient.sql("SELECT COUNT(*) FROM conhecimento_estudo")
                .query(Long.class).single();
    }

    public long countByMateria(String materia) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM conhecimento_estudo
                WHERE materia = :materia
                """)
                .param("materia", materia)
                .query(Long.class).single();
    }
}