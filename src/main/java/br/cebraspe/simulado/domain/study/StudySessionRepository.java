package br.cebraspe.simulado.domain.study;



import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class StudySessionRepository {

    private final JdbcClient jdbcClient;

    public StudySessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Long create(Long topicId) {
        return jdbcClient.sql("""
                INSERT INTO study_sessions
                    (topic_id, total_questions, correct_count, wrong_count, skipped_count)
                VALUES (:topicId, 0, 0, 0, 0)
                RETURNING id
                """)
                .param("topicId", topicId)
                .query(Long.class)
                .single();
    }

    public void finish(Long id, int correct, int wrong, int skipped) {
        jdbcClient.sql("""
                UPDATE study_sessions
                SET finished_at   = NOW(),
                    correct_count = :correct,
                    wrong_count   = :wrong,
                    skipped_count = :skipped
                WHERE id = :id
                """)
                .param("correct", correct)
                .param("wrong",   wrong)
                .param("skipped", skipped)
                .param("id",      id)
                .update();
    }

    public Optional<StudySession> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, topic_id, started_at, finished_at,
                       total_questions, correct_count, wrong_count, skipped_count
                FROM study_sessions WHERE id = :id
                """)
                .param("id", id)
                .query(StudySession.class)
                .optional();
    }
}