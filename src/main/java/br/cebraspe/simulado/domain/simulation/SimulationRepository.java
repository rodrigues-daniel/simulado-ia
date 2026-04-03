package br.cebraspe.simulado.domain.simulation;

import br.cebraspe.simulado.domain.question.Question;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class SimulationRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public SimulationRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public Simulation save(Simulation s) {
        if (s.id() == null) {
            var id = jdbcClient.sql("""
                    INSERT INTO simulations (contest_id, name, total_questions,
                                            time_limit_min, status)
                    VALUES (:contestId, :name, :totalQuestions, :timeLimitMin, :status)
                    RETURNING id
                    """)
                    .param("contestId", s.contestId())
                    .param("name", s.name())
                    .param("totalQuestions", s.totalQuestions())
                    .param("timeLimitMin", s.timeLimitMin())
                    .param("status", s.status())
                    .query(Long.class).single();
            return findById(id).orElseThrow();
        }
        return s;
    }

    public Optional<Simulation> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, contest_id, name, total_questions, time_limit_min,
                       status, started_at, finished_at, created_at
                FROM simulations WHERE id = :id
                """)
                .param("id", id)
                .query(Simulation.class)
                .optional();
    }

    public void saveSimulationQuestions(Long simulationId, List<Question> questions) {
        for (int i = 0; i < questions.size(); i++) {
            jdbcClient.sql("""
                    INSERT INTO simulation_questions (simulation_id, question_id, order_number)
                    VALUES (:simId, :questionId, :order)
                    """)
                    .param("simId", simulationId)
                    .param("questionId", questions.get(i).id())
                    .param("order", i + 1)
                    .update();
        }
    }

    public List<SimulationQuestion> findSimulationQuestions(Long simulationId) {
        return jdbcClient.sql("""
                SELECT id, simulation_id, question_id, order_number,
                       user_answer, is_skipped, answered_at
                FROM simulation_questions
                WHERE simulation_id = :simId ORDER BY order_number
                """)
                .param("simId", simulationId)
                .query(SimulationQuestion.class)
                .list();
    }

    public void updateAnswer(Long sqId, Boolean answer, boolean skipped) {
        jdbcClient.sql("""
                UPDATE simulation_questions
                SET user_answer = :answer, is_skipped = :skipped, answered_at = NOW()
                WHERE id = :id
                """)
                .param("answer", answer)
                .param("skipped", skipped)
                .param("id", sqId)
                .update();
    }

    public void updateStatus(Long id, String status, LocalDateTime finishedAt) {
        jdbcClient.sql("""
                UPDATE simulations SET status = :status, finished_at = :finishedAt
                WHERE id = :id
                """)
                .param("status", status)
                .param("finishedAt", finishedAt)
                .param("id", id)
                .update();
    }

    public SimulationResult saveResult(SimulationResult r) {
        jdbcClient.sql("""
                INSERT INTO simulation_results (simulation_id, total_questions, answered_count,
                  correct_count, wrong_count, skipped_count, gross_score, net_score,
                  guessing_tendency_pct, risk_level, keyword_traps_hit, report_json)
                VALUES (:simId, :total, :answered, :correct, :wrong, :skipped,
                        :gross, :net, :guessPct, :risk,
                        :traps::text[], :reportJson::jsonb)
                ON CONFLICT (simulation_id) DO UPDATE SET
                  correct_count = EXCLUDED.correct_count,
                  net_score = EXCLUDED.net_score
                """)
                .param("simId", r.simulationId())
                .param("total", r.totalQuestions())
                .param("answered", r.answeredCount())
                .param("correct", r.correctCount())
                .param("wrong", r.wrongCount())
                .param("skipped", r.skippedCount())
                .param("gross", r.grossScore())
                .param("net", r.netScore())
                .param("guessPct", r.guessingTendencyPct())
                .param("risk", r.riskLevel())
                .param("traps", r.keywordTrapsHit() != null
                        ? r.keywordTrapsHit().toArray(String[]::new)
                        : new String[] {})
                .param("reportJson", r.reportJson())
                .update();
        return findResult(r.simulationId()).orElseThrow();
    }

    public Optional<SimulationResult> findResult(Long simulationId) {
        return jdbcClient.sql("""
                SELECT id, simulation_id, total_questions, answered_count,
                       correct_count, wrong_count, skipped_count, gross_score,
                       net_score, guessing_tendency_pct, risk_level,
                       keyword_traps_hit, report_json::text, calculated_at
                FROM simulation_results WHERE simulation_id = :simId
                """)
                .param("simId", simulationId)
                .query((rs, n) -> {
                    var traps = rs.getArray("keyword_traps_hit");
                    return new SimulationResult(
                            rs.getLong("id"), rs.getLong("simulation_id"),
                            rs.getInt("total_questions"), rs.getInt("answered_count"),
                            rs.getInt("correct_count"), rs.getInt("wrong_count"),
                            rs.getInt("skipped_count"), rs.getBigDecimal("gross_score"),
                            rs.getBigDecimal("net_score"),
                            rs.getBigDecimal("guessing_tendency_pct"),
                            rs.getString("risk_level"),
                            traps != null ? Arrays.asList((String[]) traps.getArray()) : List.of(),
                            rs.getString("report_json"),
                            rs.getTimestamp("calculated_at").toLocalDateTime());
                })
                .optional();
    }

    public record SimulationQuestion(
            Long id, Long simulationId, Long questionId,
            Integer orderNumber, Boolean userAnswer,
            Boolean isSkipped, LocalDateTime answeredAt) {
    }
}