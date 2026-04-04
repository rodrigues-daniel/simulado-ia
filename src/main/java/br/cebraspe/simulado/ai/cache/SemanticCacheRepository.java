package br.cebraspe.simulado.ai.cache;

import br.cebraspe.simulado.ai.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class SemanticCacheRepository {

    private static final Logger log =
            LoggerFactory.getLogger(SemanticCacheRepository.class);

    // Limiar de similaridade para cache hit (distância cosseno < 0.1 = 90%+ similar)
    private static final double CACHE_DISTANCE_THRESHOLD = 0.1;

    private final JdbcClient      jdbcClient;
    private final EmbeddingService embeddingService;

    public SemanticCacheRepository(JdbcClient jdbcClient,
                                    EmbeddingService embeddingService) {
        this.jdbcClient       = jdbcClient;
        this.embeddingService = embeddingService;
    }

    public record CacheEntry(
            Long   id,
            String pergunta,
            String resposta,
            String contexto,
            int    hits,
            LocalDateTime createdAt,
            LocalDateTime lastHitAt
    ) {}

    /**
     * Busca no cache semântico usando distância de cosseno.
     * Retorna resposta se distância < 0.1 (muito similar).
     *
     * @param pergunta texto da pergunta do usuário
     * @return Optional com a resposta cached
     */
    public Optional<CacheHit> findSimilar(String pergunta) {
        try {
            String vector = embeddingService.embedToString(pergunta);

            var result = jdbcClient.sql("""
                    SELECT id, pergunta, resposta, contexto, hits,
                           created_at, last_hit_at,
                           (embedding <=> :vec::vector) AS distance
                    FROM cache_questoes
                    WHERE embedding IS NOT NULL
                      AND (embedding <=> :vec::vector) < :threshold
                    ORDER BY embedding <=> :vec::vector
                    LIMIT 1
                    """)
                    .param("vec",       vector)
                    .param("threshold", CACHE_DISTANCE_THRESHOLD)
                    .query((rs, n) -> new CacheHit(
                            rs.getLong("id"),
                            rs.getString("resposta"),
                            rs.getString("contexto"),
                            rs.getDouble("distance"),
                            rs.getInt("hits")
                    ))
                    .optional();

            // Incrementa hit counter se encontrou
            result.ifPresent(hit -> {
                incrementHit(hit.id());
                log.debug("Cache hit! id={} distância={:.4f}",
                        hit.id(), hit.distance());
            });

            return result;

        } catch (Exception e) {
            log.warn("Erro na busca de cache semântico: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Salva pergunta + resposta no cache com embedding da pergunta.
     * Fecha o ciclo: sempre chamado após resposta do Ollama.
     */
    public Long save(String pergunta, String resposta, String contexto) {
        try {
            String vector = embeddingService.embedToString(pergunta);

            return jdbcClient.sql("""
                    INSERT INTO cache_questoes
                        (pergunta, resposta, contexto, embedding)
                    VALUES
                        (:pergunta, :resposta, :contexto, :embedding::vector)
                    ON CONFLICT DO NOTHING
                    RETURNING id
                    """)
                    .param("pergunta",  pergunta)
                    .param("resposta",  resposta)
                    .param("contexto",  contexto)
                    .param("embedding", vector)
                    .query(Long.class)
                    .optional()
                    .orElse(null);

        } catch (Exception e) {
            log.warn("Falha ao salvar cache semântico: {}", e.getMessage());
            return null;
        }
    }

    private void incrementHit(Long id) {
        jdbcClient.sql("""
                UPDATE cache_questoes
                SET hits = hits + 1, last_hit_at = NOW()
                WHERE id = :id
                """)
                .param("id", id)
                .update();
    }

    public void deleteById(Long id) {
        jdbcClient.sql("DELETE FROM cache_questoes WHERE id = :id")
                .param("id", id).update();
    }

    public java.util.List<CacheStats> getTopHits(int limit) {
        return jdbcClient.sql("""
                SELECT id, pergunta, hits, created_at, last_hit_at
                FROM cache_questoes
                ORDER BY hits DESC
                LIMIT :limit
                """)
                .param("limit", limit)
                .query(CacheStats.class)
                .list();
    }

    public long count() {
        return jdbcClient.sql("SELECT COUNT(*) FROM cache_questoes")
                .query(Long.class).single();
    }

    public record CacheHit(
            Long   id,
            String resposta,
            String contexto,
            double distance,
            int    hits
    ) {}

    public record CacheStats(
            Long   id,
            String pergunta,
            int    hits,
            LocalDateTime createdAt,
            LocalDateTime lastHitAt
    ) {}
}