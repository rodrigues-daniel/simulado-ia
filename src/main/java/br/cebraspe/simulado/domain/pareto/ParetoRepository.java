package br.cebraspe.simulado.domain.pareto;


import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public class ParetoRepository {

    private final JdbcClient jdbcClient;

    public ParetoRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void upsertUserPerformance(Long topicId, boolean isCorrect) {
        int correct = isCorrect ? 1 : 0;
        int wrong   = isCorrect ? 0 : 1;

        jdbcClient.sql("""
                INSERT INTO user_topic_performance
                    (topic_id, total_answered, total_correct, total_wrong,
                     accuracy_rate, last_studied, mastery_level, updated_at)
                VALUES
                    (:topicId, 1, :correct, :wrong, :accuracyInit, NOW(), 'INICIANTE', NOW())
                ON CONFLICT (topic_id) DO UPDATE SET
                    total_answered = user_topic_performance.total_answered + 1,
                    total_correct  = user_topic_performance.total_correct  + :correct,
                    total_wrong    = user_topic_performance.total_wrong    + :wrong,
                    accuracy_rate  =
                        (user_topic_performance.total_correct + :correct)::numeric
                        / (user_topic_performance.total_answered + 1)::numeric,
                    last_studied   = NOW(),
                    mastery_level  = CASE
                        WHEN (user_topic_performance.total_correct + :correct)::numeric
                             / (user_topic_performance.total_answered + 1)::numeric >= 0.85
                             THEN 'AVANCADO'
                        WHEN (user_topic_performance.total_correct + :correct)::numeric
                             / (user_topic_performance.total_answered + 1)::numeric >= 0.65
                             THEN 'INTERMEDIARIO'
                        ELSE 'INICIANTE'
                    END,
                    updated_at = NOW()
                """)
                .param("topicId",      topicId)
                .param("correct",      correct)
                .param("wrong",        wrong)
                .param("accuracyInit", isCorrect ? 1.0 : 0.0)
                .update();
    }

    public List<ParetoAnalysis> findTopByContest(Long contestId, BigDecimal threshold) {
        return jdbcClient.sql("""
                SELECT pa.id, pa.topic_id, pa.contest_id, pa.incidence_rate,
                       pa.question_count, pa.avg_difficulty, pa.is_pareto_top,
                       pa.last_updated,
                       t.name        AS topic_name,
                       t.discipline
                FROM pareto_analysis pa
                JOIN topics t ON pa.topic_id = t.id
                WHERE pa.contest_id    = :contestId
                  AND pa.incidence_rate >= :threshold
                  AND t.is_hidden      = FALSE
                ORDER BY pa.incidence_rate DESC
                """)
                .param("contestId", contestId)
                .param("threshold", threshold)
                .query((rs, n) -> new ParetoAnalysis(
                        rs.getLong("id"),
                        rs.getLong("topic_id"),
                        rs.getObject("contest_id", Long.class),
                        rs.getBigDecimal("incidence_rate"),
                        rs.getInt("question_count"),
                        rs.getBigDecimal("avg_difficulty"),
                        rs.getBoolean("is_pareto_top"),
                        rs.getTimestamp("last_updated").toLocalDateTime(),
                        rs.getString("topic_name"),
                        rs.getString("discipline")
                ))
                .list();
    }

    public List<UserTopicPerformance> getUserPerformanceByContest(Long contestId) {
        return jdbcClient.sql("""
                SELECT utp.id, utp.topic_id, utp.total_answered, utp.total_correct,
                       utp.total_wrong, utp.accuracy_rate, utp.last_studied,
                       utp.mastery_level, utp.updated_at
                FROM user_topic_performance utp
                JOIN topics t ON utp.topic_id = t.id
                WHERE t.contest_id = :contestId
                ORDER BY utp.accuracy_rate ASC
                """)
                .param("contestId", contestId)
                .query(UserTopicPerformance.class)
                .list();
    }

    public List<KeywordPerformance> getKeywordPerformance() {
        return jdbcClient.sql("""
                SELECT ukp.id, ukp.keyword_id, tk.keyword, tk.category,
                       ukp.wrong_count, ukp.total_seen, ukp.error_rate
                FROM user_keyword_performance ukp
                JOIN trap_keywords tk ON ukp.keyword_id = tk.id
                ORDER BY ukp.wrong_count DESC
                """)
                .query(KeywordPerformance.class)
                .list();
    }

    public List<Long> findAllContestIds() {
        return jdbcClient.sql("SELECT DISTINCT id FROM contests")
                .query(Long.class)
                .list();
    }

    public record UserTopicPerformance(
            Long id,
            Long topicId,
            Integer totalAnswered,
            Integer totalCorrect,
            Integer totalWrong,
            BigDecimal accuracyRate,
            java.time.LocalDateTime lastStudied,
            String masteryLevel,
            java.time.LocalDateTime updatedAt
    ) {}

    public record KeywordPerformance(
            Long id,
            Long keywordId,
            String keyword,
            String category,
            Integer wrongCount,
            Integer totalSeen,
            BigDecimal errorRate
    ) {}
}