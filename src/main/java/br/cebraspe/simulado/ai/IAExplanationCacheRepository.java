package br.cebraspe.simulado.ai;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class IAExplanationCacheRepository {

    private final JdbcClient jdbcClient;

    public IAExplanationCacheRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record CachedExplanation(
            Long id,
            Long questionId,
            Boolean userAnswer,
            String explanation,
            Boolean ragSource,
            BigDecimal ragAvgScore,
            String modelUsed,
            LocalDateTime createdAt
    ) {}

    public Optional<CachedExplanation> find(Long questionId, Boolean userAnswer) {
        return jdbcClient.sql("""
                SELECT id, question_id, user_answer, explanation,
                       rag_source, rag_avg_score, model_used, created_at
                FROM ia_explanation_cache
                WHERE question_id = :questionId
                  AND user_answer = :userAnswer
                """)
                .param("questionId", questionId)
                .param("userAnswer", userAnswer)
                .query(CachedExplanation.class)
                .optional();
    }

    public void save(Long questionId, Boolean userAnswer, String explanation,
                     boolean ragSource, Double ragAvgScore) {
        jdbcClient.sql("""
                INSERT INTO ia_explanation_cache
                    (question_id, user_answer, explanation,
                     rag_source, rag_avg_score, model_used)
                VALUES
                    (:questionId, :userAnswer, :explanation,
                     :ragSource, :ragAvgScore, 'llama3.2:3b')
                ON CONFLICT (question_id, user_answer)
                DO UPDATE SET
                    explanation  = EXCLUDED.explanation,
                    rag_source   = EXCLUDED.rag_source,
                    rag_avg_score= EXCLUDED.rag_avg_score,
                    created_at   = NOW()
                """)
                .param("questionId",  questionId)
                .param("userAnswer",  userAnswer)
                .param("explanation", explanation)
                .param("ragSource",   ragSource)
                .param("ragAvgScore", ragAvgScore != null
                        ? BigDecimal.valueOf(ragAvgScore) : null)
                .update();
    }
}