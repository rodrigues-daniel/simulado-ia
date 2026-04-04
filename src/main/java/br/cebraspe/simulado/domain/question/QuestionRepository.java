package br.cebraspe.simulado.domain.question;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class QuestionRepository {

    private final JdbcClient jdbcClient;

    public QuestionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // ── SQL base reutilizado em todos os SELECTs ────────────────────────
    private static final String SELECT_ALL = """
            SELECT id, topic_id, contest_id, statement, correct_answer,
                   law_paragraph, law_reference, explanation, professor_tip,
                   trap_keywords, year, source, difficulty,
                   ia_reviewed, ia_approved, ia_confidence,
                   review_note, reviewed_at, created_at
            FROM questions
            """;

    public List<Question> findByTopicId(Long topicId) {
        return jdbcClient.sql(SELECT_ALL + """
                WHERE topic_id = :topicId
                ORDER BY RANDOM()
                """)
                .param("topicId", topicId)
                .query((rs, n) -> mapQuestion(rs))
                .list();
    }

    public List<Question> findForSimulation(Long contestId, Integer limit) {
        return jdbcClient.sql("""
                SELECT q.id, q.topic_id, q.contest_id, q.statement, q.correct_answer,
                       q.law_paragraph, q.law_reference, q.explanation, q.professor_tip,
                       q.trap_keywords, q.year, q.source, q.difficulty,
                       q.ia_reviewed, q.ia_approved, q.ia_confidence,
                       q.review_note, q.reviewed_at, q.created_at
                FROM questions q
                JOIN topics t ON q.topic_id = t.id
                WHERE t.contest_id  = :contestId
                  AND t.is_priority = TRUE
                  AND t.is_hidden   = FALSE
                ORDER BY t.incidence_rate DESC, RANDOM()
                LIMIT :limit
                """)
                .param("contestId", contestId)
                .param("limit",     limit)
                .query((rs, n) -> mapQuestion(rs))
                .list();
    }

    public Optional<Question> findById(Long id) {
        return jdbcClient.sql(SELECT_ALL + "WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> mapQuestion(rs))
                .optional();
    }

    public Question save(Question question) {
        if (question.id() == null) {
            var id = jdbcClient.sql("""
                    INSERT INTO questions (
                        topic_id, contest_id, statement, correct_answer,
                        law_paragraph, law_reference, explanation, professor_tip,
                        trap_keywords, year, source, difficulty,
                        ia_reviewed, ia_approved, ia_confidence,
                        review_note, reviewed_at
                    ) VALUES (
                        :topicId, :contestId, :statement, :correctAnswer,
                        :lawParagraph, :lawReference, :explanation, :professorTip,
                        :trapKeywords::text[], :year, :source, :difficulty,
                        :iaReviewed, :iaApproved, :iaConfidence,
                        :reviewNote, :reviewedAt
                    )
                    RETURNING id
                    """)
                    .param("topicId",      question.topicId())
                    .param("contestId",    question.contestId())
                    .param("statement",    question.statement())
                    .param("correctAnswer",question.correctAnswer())
                    .param("lawParagraph", question.lawParagraph())
                    .param("lawReference", question.lawReference())
                    .param("explanation",  question.explanation())
                    .param("professorTip", question.professorTip())
                    .param("trapKeywords", question.trapKeywords() != null
                            ? question.trapKeywords().toArray(String[]::new)
                            : new String[]{})
                    .param("year",         question.year())
                    .param("source",       question.source())
                    .param("difficulty",   question.difficulty())
                    .param("iaReviewed",   question.iaReviewed()   != null
                            ? question.iaReviewed() : false)
                    .param("iaApproved",   question.iaApproved())
                    .param("iaConfidence", question.iaConfidence())
                    .param("reviewNote",   question.reviewNote())
                    .param("reviewedAt",   question.reviewedAt())
                    .query(Long.class)
                    .single();
            return findById(id).orElseThrow();
        }
        return question;
    }

    public void saveAnswer(QuestionAnswer answer) {
        jdbcClient.sql("""
                INSERT INTO question_answers
                    (session_id, question_id, user_answer, is_correct, time_spent_ms)
                VALUES
                    (:sessionId, :questionId, :userAnswer, :isCorrect, :timeSpentMs)
                """)
                .param("sessionId",   answer.sessionId())
                .param("questionId",  answer.questionId())
                .param("userAnswer",  answer.userAnswer())
                .param("isCorrect",   answer.isCorrect())
                .param("timeSpentMs", answer.timeSpentMs() != null
                        ? answer.timeSpentMs() : 0)
                .update();
    }

    // ── Métodos IA ──────────────────────────────────────────────────────

    public List<Question> findBySource(String source) {
        return jdbcClient.sql(SELECT_ALL + """
                WHERE source = :source
                ORDER BY created_at DESC
                """)
                .param("source", source)
                .query((rs, n) -> mapQuestion(rs))
                .list();
    }

    public List<Question> findIAQuestionsByTopic(Long topicId) {
        return jdbcClient.sql(SELECT_ALL + """
                WHERE topic_id = :topicId
                  AND source   = 'IA-GERADA'
                ORDER BY created_at DESC
                """)
                .param("topicId", topicId)
                .query((rs, n) -> mapQuestion(rs))
                .list();
    }

    public List<IAQuestionSummary> findIAQuestionsSummaryByTopic(Long topicId) {
        return jdbcClient.sql("""
                SELECT q.id, q.topic_id, q.statement, q.correct_answer,
                       q.difficulty, q.ia_reviewed, q.ia_approved,
                       q.ia_confidence, q.review_note, q.reviewed_at,
                       q.created_at, t.name AS topic_name, t.discipline
                FROM questions q
                JOIN topics t ON q.topic_id = t.id
                WHERE q.topic_id = :topicId
                  AND q.source   = 'IA-GERADA'
                ORDER BY q.ia_reviewed ASC, q.created_at DESC
                """)
                .param("topicId", topicId)
                .query((rs, n) -> mapSummary(rs))
                .list();
    }

    public List<IAQuestionSummary> findAllIAQuestionsUnreviewed() {
        return jdbcClient.sql("""
                SELECT q.id, q.topic_id, q.statement, q.correct_answer,
                       q.difficulty, q.ia_reviewed, q.ia_approved,
                       q.ia_confidence, q.review_note, q.reviewed_at,
                       q.created_at, t.name AS topic_name, t.discipline
                FROM questions q
                JOIN topics t ON q.topic_id = t.id
                WHERE q.source      = 'IA-GERADA'
                  AND q.ia_reviewed = FALSE
                ORDER BY q.created_at DESC
                """)
                .query((rs, n) -> mapSummary(rs))
                .list();
    }

    public IAQuestionStats getIAStats() {
        return jdbcClient.sql("""
                SELECT
                    COUNT(*)                                              AS total,
                    COUNT(*) FILTER (WHERE ia_reviewed = TRUE)            AS reviewed,
                    COUNT(*) FILTER (WHERE ia_approved = TRUE)            AS approved,
                    COUNT(*) FILTER (WHERE ia_approved = FALSE
                                      AND ia_reviewed  = TRUE)            AS rejected,
                    COUNT(*) FILTER (WHERE ia_reviewed = FALSE)           AS pending,
                    ROUND(AVG(ia_confidence)::numeric, 3)                 AS avg_confidence
                FROM questions
                WHERE source = 'IA-GERADA'
                """)
                .query((rs, n) -> new IAQuestionStats(
                        rs.getLong("total"),
                        rs.getLong("reviewed"),
                        rs.getLong("approved"),
                        rs.getLong("rejected"),
                        rs.getLong("pending"),
                        rs.getObject("avg_confidence", BigDecimal.class)
                ))
                .single();
    }

    public void reviewQuestion(Long id, boolean approved, String note) {
        jdbcClient.sql("""
                UPDATE questions SET
                    ia_reviewed = TRUE,
                    ia_approved = :approved,
                    review_note = :note,
                    reviewed_at = NOW()
                WHERE id = :id
                """)
                .param("approved", approved)
                .param("note",     note)
                .param("id",       id)
                .update();
    }

    public void deleteById(Long id) {
        jdbcClient.sql("""
                DELETE FROM questions
                WHERE id = :id AND source = 'IA-GERADA'
                """)
                .param("id", id)
                .update();
    }

    public int countByTopicId(Long topicId) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM questions WHERE topic_id = :topicId
                """)
                .param("topicId", topicId)
                .query(Integer.class)
                .single();
    }

    public int countApprovedByTopicId(Long topicId) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM questions
                WHERE topic_id = :topicId
                  AND (source != 'IA-GERADA'
                       OR ia_approved = TRUE
                       OR ia_reviewed = FALSE)
                """)
                .param("topicId", topicId)
                .query(Integer.class)
                .single();
    }

    // ── Mappers ─────────────────────────────────────────────────────────

    private Question mapQuestion(ResultSet rs) throws SQLException {
        Array trapArray = rs.getArray("trap_keywords");
        List<String> traps = trapArray != null
                ? Arrays.asList((String[]) trapArray.getArray())
                : List.of();

        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        Timestamp createdAt  = rs.getTimestamp("created_at");

        return new Question(
                rs.getLong("id"),
                rs.getLong("topic_id"),
                rs.getObject("contest_id",    Long.class),
                rs.getString("statement"),
                rs.getBoolean("correct_answer"),
                rs.getString("law_paragraph"),
                rs.getString("law_reference"),
                rs.getString("explanation"),
                rs.getString("professor_tip"),
                traps,
                rs.getObject("year",          Integer.class),
                rs.getString("source"),
                rs.getString("difficulty"),
                // ── Campos IA — leitura defensiva ──────────────────────
                rs.getBoolean("ia_reviewed"),
                rs.getObject("ia_approved",   Boolean.class),
                rs.getObject("ia_confidence", BigDecimal.class),
                rs.getString("review_note"),
                reviewedAt != null ? reviewedAt.toLocalDateTime() : null,
                // ───────────────────────────────────────────────────────
                createdAt  != null ? createdAt.toLocalDateTime()  : null
        );
    }

    private IAQuestionSummary mapSummary(ResultSet rs) throws SQLException {
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        Timestamp createdAt  = rs.getTimestamp("created_at");
        return new IAQuestionSummary(
                rs.getLong("id"),
                rs.getLong("topic_id"),
                rs.getString("statement"),
                rs.getBoolean("correct_answer"),
                rs.getString("difficulty"),
                rs.getBoolean("ia_reviewed"),
                rs.getObject("ia_approved",   Boolean.class),
                rs.getObject("ia_confidence", BigDecimal.class),
                rs.getString("review_note"),
                reviewedAt != null ? reviewedAt.toLocalDateTime() : null,
                createdAt  != null ? createdAt.toLocalDateTime()  : null,
                rs.getString("topic_name"),
                rs.getString("discipline")
        );
    }

    public List<Question> findForSimulationBySource(Long contestId,
                                                    String source,
                                                    Integer limit) {
        String sourceFilter = "IA-GERADA".equals(source)
                ? "AND q.source = 'IA-GERADA' AND (q.ia_approved = TRUE OR q.ia_reviewed = FALSE)"
                : "AND (q.source IS NULL OR q.source != 'IA-GERADA')";

        return jdbcClient.sql("""
            SELECT q.id, q.topic_id, q.contest_id, q.statement, q.correct_answer,
                   q.law_paragraph, q.law_reference, q.explanation, q.professor_tip,
                   q.trap_keywords, q.year, q.source, q.difficulty,
                   q.ia_reviewed, q.ia_approved, q.ia_confidence,
                   q.review_note, q.reviewed_at, q.created_at
            FROM questions q
            JOIN topics t ON q.topic_id = t.id
            WHERE t.contest_id  = :contestId
              AND t.is_priority = TRUE
              AND t.is_hidden   = FALSE
            """ + sourceFilter + """
            ORDER BY t.incidence_rate DESC, RANDOM()
            LIMIT :limit
            """)
                .param("contestId", contestId)
                .param("limit",     limit)
                .query((rs, n) -> mapQuestion(rs))
                .list();
    }

    // ── Records de suporte ──────────────────────────────────────────────

    public record IAQuestionSummary(
            Long id, Long topicId, String statement,
            Boolean correctAnswer, String difficulty,
            Boolean iaReviewed, Boolean iaApproved,
            BigDecimal iaConfidence, String reviewNote,
            java.time.LocalDateTime reviewedAt,
            java.time.LocalDateTime createdAt,
            String topicName, String discipline
    ) {}

    public record IAQuestionStats(
            Long total, Long reviewed, Long approved,
            Long rejected, Long pending,
            BigDecimal avgConfidence
    ) {}
}