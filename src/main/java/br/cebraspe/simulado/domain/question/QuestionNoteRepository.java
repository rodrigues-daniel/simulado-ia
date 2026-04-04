package br.cebraspe.simulado.domain.question;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class QuestionNoteRepository {

    private final JdbcClient jdbcClient;

    public QuestionNoteRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record QuestionNote(
            Long id,
            Long questionId,
            String note,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public List<QuestionNote> findByQuestionId(Long questionId) {
        return jdbcClient.sql("""
                SELECT id, question_id, note, created_at, updated_at
                FROM question_notes
                WHERE question_id = :questionId
                ORDER BY created_at DESC
                """)
                .param("questionId", questionId)
                .query(QuestionNote.class)
                .list();
    }

    public QuestionNote save(Long questionId, String note) {
        var id = jdbcClient.sql("""
                INSERT INTO question_notes (question_id, note)
                VALUES (:questionId, :note)
                RETURNING id
                """)
                .param("questionId", questionId)
                .param("note", note)
                .query(Long.class)
                .single();

        return findById(id).orElseThrow();
    }

    public QuestionNote update(Long id, String note) {
        jdbcClient.sql("""
                UPDATE question_notes
                SET note = :note, updated_at = NOW()
                WHERE id = :id
                """)
                .param("note", note)
                .param("id", id)
                .update();
        return findById(id).orElseThrow();
    }

    public void delete(Long id) {
        jdbcClient.sql("DELETE FROM question_notes WHERE id = :id")
                .param("id", id)
                .update();
    }

    public Optional<QuestionNote> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, question_id, note, created_at, updated_at
                FROM question_notes WHERE id = :id
                """)
                .param("id", id)
                .query(QuestionNote.class)
                .optional();
    }
}