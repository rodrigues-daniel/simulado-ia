package br.cebraspe.simulado.domain.question;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SavedQuestionRepository {

    private final JdbcClient jdbcClient;

    public SavedQuestionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record SavedQuestion(
            Long id,
            Long questionId,
            LocalDateTime savedAt
    ) {}

    public record SavedQuestionDetail(
            Long id,
            Long questionId,
            String statement,
            Boolean correctAnswer,
            String difficulty,
            String source,
            String topicName,
            String discipline,
            String lawReference,
            LocalDateTime savedAt
    ) {}

    public boolean isSaved(Long questionId) {
        var count = jdbcClient.sql("""
                SELECT COUNT(*) FROM saved_questions
                WHERE question_id = :questionId
                """)
                .param("questionId", questionId)
                .query(Integer.class)
                .single();
        return count > 0;
    }

    public SavedQuestion save(Long questionId) {
        jdbcClient.sql("""
                INSERT INTO saved_questions (question_id)
                VALUES (:questionId)
                ON CONFLICT (question_id) DO NOTHING
                """)
                .param("questionId", questionId)
                .update();

        var id = jdbcClient.sql("""
                SELECT id FROM saved_questions
                WHERE question_id = :questionId
                """)
                .param("questionId", questionId)
                .query(Long.class)
                .single();

        return new SavedQuestion(id, questionId, LocalDateTime.now());
    }

    public void remove(Long questionId) {
        jdbcClient.sql("""
                DELETE FROM saved_questions WHERE question_id = :questionId
                """)
                .param("questionId", questionId)
                .update();
    }

    public List<SavedQuestionDetail> findAllWithDetails() {
        return jdbcClient.sql("""
                SELECT sq.id, sq.question_id, q.statement, q.correct_answer,
                       q.difficulty, q.source, t.name AS topic_name,
                       t.discipline, q.law_reference, sq.saved_at
                FROM saved_questions sq
                JOIN questions q ON sq.question_id = q.id
                JOIN topics    t ON q.topic_id     = t.id
                ORDER BY sq.saved_at DESC
                """)
                .query(SavedQuestionDetail.class)
                .list();
    }
}