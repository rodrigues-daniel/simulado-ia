package br.cebraspe.simulado.domain.question;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class QuestionRepository {

    private final JdbcClient jdbcClient;

    public QuestionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Question> findByTopicId(Long topicId) {
        return jdbcClient.sql("""
                SELECT id, topic_id, contest_id, statement, correct_answer,
                       law_paragraph, law_reference, explanation, professor_tip,
                       trap_keywords, year, source, difficulty, created_at
                FROM questions WHERE topic_id = :topicId
                ORDER BY RANDOM()
                """)
                .param("topicId", topicId)
                .query((rs, rowNum) -> mapQuestion(rs))
                .list();
    }

    public List<Question> findForSimulation(Long contestId, Integer limit) {
        return jdbcClient.sql("""
                SELECT q.id, q.topic_id, q.contest_id, q.statement, q.correct_answer,
                       q.law_paragraph, q.law_reference, q.explanation, q.professor_tip,
                       q.trap_keywords, q.year, q.source, q.difficulty, q.created_at
                FROM questions q
                JOIN topics t ON q.topic_id = t.id
                WHERE t.contest_id = :contestId
                  AND t.is_priority = TRUE
                  AND t.is_hidden = FALSE
                ORDER BY t.incidence_rate DESC, RANDOM()
                LIMIT :limit
                """)
                .param("contestId", contestId)
                .param("limit", limit)
                .query((rs, rowNum) -> mapQuestion(rs))
                .list();
    }

    public Optional<Question> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, topic_id, contest_id, statement, correct_answer,
                       law_paragraph, law_reference, explanation, professor_tip,
                       trap_keywords, year, source, difficulty, created_at
                FROM questions WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> mapQuestion(rs))
                .optional();
    }

    public Question save(Question question) {
        if (question.id() == null) {
            var id = jdbcClient.sql("""
                    INSERT INTO questions (topic_id, contest_id, statement, correct_answer,
                                          law_paragraph, law_reference, explanation,
                                          professor_tip, trap_keywords, year, source, difficulty)
                    VALUES (:topicId, :contestId, :statement, :correctAnswer,
                            :lawParagraph, :lawReference, :explanation,
                            :professorTip, :trapKeywords::text[], :year, :source, :difficulty)
                    RETURNING id
                    """)
                    .param("topicId", question.topicId())
                    .param("contestId", question.contestId())
                    .param("statement", question.statement())
                    .param("correctAnswer", question.correctAnswer())
                    .param("lawParagraph", question.lawParagraph())
                    .param("lawReference", question.lawReference())
                    .param("explanation", question.explanation())
                    .param("professorTip", question.professorTip())
                    .param("trapKeywords", question.trapKeywords() != null
                            ? question.trapKeywords().toArray(String[]::new)
                            : new String[] {})
                    .param("year", question.year())
                    .param("source", question.source())
                    .param("difficulty", question.difficulty())
                    .query(Long.class)
                    .single();
            return findById(id).orElseThrow();
        }
        return question;
    }

    public void saveAnswer(QuestionAnswer answer) {
        jdbcClient.sql("""
                INSERT INTO question_answers (session_id, question_id, user_answer,
                                             is_correct, time_spent_ms)
                VALUES (:sessionId, :questionId, :userAnswer, :isCorrect, :timeSpentMs)
                """)
                .param("sessionId", answer.sessionId())
                .param("questionId", answer.questionId())
                .param("userAnswer", answer.userAnswer())
                .param("isCorrect", answer.isCorrect())
                .param("timeSpentMs", answer.timeSpentMs())
                .update();
    }

    private Question mapQuestion(java.sql.ResultSet rs) throws java.sql.SQLException {
        var trapArray = rs.getArray("trap_keywords");
        List<String> traps = trapArray != null
                ? Arrays.asList((String[]) trapArray.getArray())
                : List.of();
        return new Question(
                rs.getLong("id"),
                rs.getLong("topic_id"),
                rs.getObject("contest_id", Long.class),
                rs.getString("statement"),
                rs.getBoolean("correct_answer"),
                rs.getString("law_paragraph"),
                rs.getString("law_reference"),
                rs.getString("explanation"),
                rs.getString("professor_tip"),
                traps,
                rs.getObject("year", Integer.class),
                rs.getString("source"),
                rs.getString("difficulty"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}