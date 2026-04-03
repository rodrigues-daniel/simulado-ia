package br.cebraspe.simulado.domain.topic;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class TopicRepository {

    private final JdbcClient jdbcClient;

    public TopicRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Topic> findByContestId(Long contestId) {
        return jdbcClient.sql("""
                SELECT id, contest_id, name, discipline, law_reference,
                       incidence_rate, is_priority, is_hidden, created_at
                FROM topics WHERE contest_id = :contestId
                ORDER BY incidence_rate DESC
                """)
                .param("contestId", contestId)
                .query(Topic.class)
                .list();
    }

    public List<Topic> findPriorityTopics(Long contestId, BigDecimal threshold) {
        return jdbcClient.sql("""
                SELECT id, contest_id, name, discipline, law_reference,
                       incidence_rate, is_priority, is_hidden, created_at
                FROM topics
                WHERE contest_id = :contestId
                  AND incidence_rate >= :threshold
                  AND is_hidden = FALSE
                ORDER BY incidence_rate DESC
                """)
                .param("contestId", contestId)
                .param("threshold", threshold)
                .query(Topic.class)
                .list();
    }

    public List<Topic> findHiddenTopics(Long contestId) {
        return jdbcClient.sql("""
                SELECT id, contest_id, name, discipline, law_reference,
                       incidence_rate, is_priority, is_hidden, created_at
                FROM topics
                WHERE contest_id = :contestId AND is_hidden = TRUE
                ORDER BY incidence_rate ASC
                """)
                .param("contestId", contestId)
                .query(Topic.class)
                .list();
    }

    public Optional<Topic> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, contest_id, name, discipline, law_reference,
                       incidence_rate, is_priority, is_hidden, created_at
                FROM topics WHERE id = :id
                """)
                .param("id", id)
                .query(Topic.class)
                .optional();
    }

    public Topic save(Topic topic) {
        if (topic.id() == null) {
            var id = jdbcClient.sql("""
                    INSERT INTO topics (contest_id, name, discipline, law_reference,
                                       incidence_rate, is_priority, is_hidden)
                    VALUES (:contestId, :name, :discipline, :lawReference,
                            :incidenceRate, :isPriority, :isHidden)
                    RETURNING id
                    """)
                    .param("contestId", topic.contestId())
                    .param("name", topic.name())
                    .param("discipline", topic.discipline())
                    .param("lawReference", topic.lawReference())
                    .param("incidenceRate", topic.incidenceRate())
                    .param("isPriority", topic.isPriority())
                    .param("isHidden", topic.isHidden())
                    .query(Long.class)
                    .single();
            return findById(id).orElseThrow();
        }
        jdbcClient.sql("""
                UPDATE topics SET name=:name, discipline=:discipline,
                law_reference=:lawReference, incidence_rate=:incidenceRate,
                is_priority=:isPriority, is_hidden=:isHidden WHERE id=:id
                """)
                .param("name", topic.name())
                .param("discipline", topic.discipline())
                .param("lawReference", topic.lawReference())
                .param("incidenceRate", topic.incidenceRate())
                .param("isPriority", topic.isPriority())
                .param("isHidden", topic.isHidden())
                .param("id", topic.id())
                .update();
        return findById(topic.id()).orElseThrow();
    }

    public void toggleHidden(Long id, boolean hidden) {
        jdbcClient.sql("UPDATE topics SET is_hidden = :hidden WHERE id = :id")
                .param("hidden", hidden)
                .param("id", id)
                .update();
    }
}