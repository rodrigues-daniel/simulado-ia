package br.cebraspe.simulado.domain.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Array;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class SimulationRepository {

    private final JdbcClient  jdbcClient;
    private final ObjectMapper objectMapper;

    public SimulationRepository(JdbcClient jdbcClient,
                                 ObjectMapper objectMapper) {
        this.jdbcClient   = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public Simulation save(Simulation s) {
        if (s.id() == null) {
            var id = jdbcClient.sql("""
                    INSERT INTO simulations (
                        contest_id, name, total_questions, time_limit_min,
                        status, modality, total_vacancies, quota_vacancies,
                        cut_score_ampla, cut_score_quota,
                        points_correct, points_wrong
                    ) VALUES (
                        :contestId, :name, :totalQuestions, :timeLimitMin,
                        :status, :modality, :totalVacancies, :quotaVacancies,
                        :cutScoreAmpla, :cutScoreQuota,
                        :pointsCorrect, :pointsWrong
                    )
                    RETURNING id
                    """)
                    .param("contestId",      s.contestId())
                    .param("name",           s.name())
                    .param("totalQuestions", s.totalQuestions())
                    .param("timeLimitMin",   s.timeLimitMin())
                    .param("status",         s.status())
                    .param("modality",       s.modality() != null
                            ? s.modality() : "AMPLA")
                    .param("totalVacancies", s.totalVacancies() != null
                            ? s.totalVacancies() : 0)
                    .param("quotaVacancies", s.quotaVacancies() != null
                            ? s.quotaVacancies() : 0)
                    .param("cutScoreAmpla",  s.cutScoreAmpla())
                    .param("cutScoreQuota",  s.cutScoreQuota())
                    .param("pointsCorrect",  s.pointsCorrect() != null
                            ? s.pointsCorrect() : BigDecimal.ONE)
                    .param("pointsWrong",    s.pointsWrong() != null
                            ? s.pointsWrong()   : BigDecimal.ONE)
                    .query(Long.class)
                    .single();
            return findById(id).orElseThrow();
        }
        return s;
    }

    public Optional<Simulation> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, contest_id, name, total_questions, time_limit_min,
                       status, started_at, finished_at, created_at,
                       modality, total_vacancies, quota_vacancies,
                       cut_score_ampla, cut_score_quota,
                       points_correct, points_wrong
                FROM simulations WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new Simulation(
                        rs.getLong("id"),
                        rs.getObject("contest_id", Long.class),
                        rs.getString("name"),
                        rs.getInt("total_questions"),
                        rs.getObject("time_limit_min", Integer.class),
                        rs.getString("status"),
                        rs.getTimestamp("started_at") != null
                                ? rs.getTimestamp("started_at").toLocalDateTime() : null,
                        rs.getTimestamp("finished_at") != null
                                ? rs.getTimestamp("finished_at").toLocalDateTime() : null,
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("modality"),
                        rs.getObject("total_vacancies", Integer.class),
                        rs.getObject("quota_vacancies", Integer.class),
                        rs.getObject("cut_score_ampla", BigDecimal.class),
                        rs.getObject("cut_score_quota", BigDecimal.class),
                        rs.getObject("points_correct",  BigDecimal.class),
                        rs.getObject("points_wrong",    BigDecimal.class)
                ))
                .optional();
    }

    public void saveSimulationQuestionIds(Long simulationId,
                                           List<Long> questionIds) {
        for (int i = 0; i < questionIds.size(); i++) {
            jdbcClient.sql("""
                    INSERT INTO simulation_questions
                        (simulation_id, question_id, order_number)
                    VALUES (:simId, :questionId, :order)
                    ON CONFLICT DO NOTHING
                    """)
                    .param("simId",      simulationId)
                    .param("questionId", questionIds.get(i))
                    .param("order",      i + 1)
                    .update();
        }
    }

    public List<SimulationQuestion> findSimulationQuestions(Long simId) {
        return jdbcClient.sql("""
                SELECT id, simulation_id, question_id, order_number,
                       user_answer, is_skipped, answered_at
                FROM simulation_questions
                WHERE simulation_id = :simId
                ORDER BY order_number
                """)
                .param("simId", simId)
                .query((rs, n) -> new SimulationQuestion(
                        rs.getLong("id"),
                        rs.getLong("simulation_id"),
                        rs.getLong("question_id"),
                        rs.getInt("order_number"),
                        rs.getObject("user_answer", Boolean.class),
                        rs.getBoolean("is_skipped"),
                        rs.getTimestamp("answered_at") != null
                                ? rs.getTimestamp("answered_at").toLocalDateTime()
                                : null
                ))
                .list();
    }

    public void updateAnswer(Long sqId, Boolean answer, boolean skipped) {
        jdbcClient.sql("""
                UPDATE simulation_questions
                SET user_answer = :answer,
                    is_skipped  = :skipped,
                    answered_at = NOW()
                WHERE id = :id
                """)
                .param("answer",  answer)
                .param("skipped", skipped)
                .param("id",      sqId)
                .update();
    }

    public void updateStatus(Long id, String status, LocalDateTime finishedAt) {
        jdbcClient.sql("""
                UPDATE simulations
                SET status      = :status,
                    finished_at = :finishedAt
                WHERE id = :id
                """)
                .param("status",     status)
                .param("finishedAt", finishedAt)
                .param("id",         id)
                .update();
    }

    public SimulationResult saveResult(SimulationResult r) {
        jdbcClient.sql("""
                INSERT INTO simulation_results (
                    simulation_id, total_questions, answered_count,
                    correct_count, wrong_count, skipped_count,
                    gross_score, net_score, guessing_tendency_pct,
                    risk_level, keyword_traps_hit, report_json
                ) VALUES (
                    :simId, :total, :answered,
                    :correct, :wrong, :skipped,
                    :gross, :net, :guessPct,
                    :risk, :traps::text[], :reportJson::jsonb
                )
                ON CONFLICT (simulation_id) DO UPDATE SET
                    correct_count        = EXCLUDED.correct_count,
                    wrong_count          = EXCLUDED.wrong_count,
                    net_score            = EXCLUDED.net_score,
                    gross_score          = EXCLUDED.gross_score,
                    guessing_tendency_pct= EXCLUDED.guessing_tendency_pct,
                    risk_level           = EXCLUDED.risk_level,
                    keyword_traps_hit    = EXCLUDED.keyword_traps_hit,
                    report_json          = EXCLUDED.report_json,
                    calculated_at        = NOW()
                """)
                .param("simId",    r.simulationId())
                .param("total",    r.totalQuestions())
                .param("answered", r.answeredCount())
                .param("correct",  r.correctCount())
                .param("wrong",    r.wrongCount())
                .param("skipped",  r.skippedCount())
                .param("gross",    r.grossScore())
                .param("net",      r.netScore())
                .param("guessPct", r.guessingTendencyPct())
                .param("risk",     r.riskLevel())
                .param("traps",    r.keywordTrapsHit() != null
                        ? r.keywordTrapsHit().toArray(String[]::new)
                        : new String[]{})
                .param("reportJson", r.reportJson())
                .update();

        return findResult(r.simulationId()).orElseThrow();
    }

    public Optional<SimulationResult> findResult(Long simulationId) {
        return jdbcClient.sql("""
                SELECT id, simulation_id, total_questions, answered_count,
                       correct_count, wrong_count, skipped_count,
                       gross_score, net_score, guessing_tendency_pct,
                       risk_level, keyword_traps_hit,
                       report_json::text, calculated_at
                FROM simulation_results
                WHERE simulation_id = :simId
                """)
                .param("simId", simulationId)
                .query((rs, n) -> {
                    Array trapsArr = rs.getArray("keyword_traps_hit");
                    List<String> traps = trapsArr != null
                            ? Arrays.asList((String[]) trapsArr.getArray())
                            : List.of();
                    return new SimulationResult(
                            rs.getLong("id"),
                            rs.getLong("simulation_id"),
                            rs.getInt("total_questions"),
                            rs.getInt("answered_count"),
                            rs.getInt("correct_count"),
                            rs.getInt("wrong_count"),
                            rs.getInt("skipped_count"),
                            rs.getBigDecimal("gross_score"),
                            rs.getBigDecimal("net_score"),
                            rs.getBigDecimal("guessing_tendency_pct"),
                            rs.getString("risk_level"),
                            traps,
                            rs.getString("report_json"),
                            rs.getTimestamp("calculated_at").toLocalDateTime()
                    );
                })
                .optional();
    }

    public record SimulationQuestion(
            Long          id,
            Long          simulationId,
            Long          questionId,
            Integer       orderNumber,
            Boolean       userAnswer,
            Boolean       isSkipped,
            LocalDateTime answeredAt
    ) {}
}