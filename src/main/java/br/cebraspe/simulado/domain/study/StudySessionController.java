package br.cebraspe.simulado.domain.study;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/study/sessions")
public class StudySessionController {

    private final JdbcClient jdbcClient;

    public StudySessionController(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody Map<String, Long> body) {
        Long topicId = body.get("topicId");
        var id = jdbcClient.sql("""
                INSERT INTO study_sessions (topic_id, total_questions)
                VALUES (:topicId, 0) RETURNING id
                """)
                .param("topicId", topicId)
                .query(Long.class).single();
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PatchMapping("/{id}/finish")
    public ResponseEntity<Void> finish(@PathVariable Long id,
            @RequestBody Map<String, Integer> stats) {
        jdbcClient.sql("""
                UPDATE study_sessions
                SET finished_at = NOW(),
                    correct_count = :correct,
                    wrong_count   = :wrong
                WHERE id = :id
                """)
                .param("correct", stats.getOrDefault("correct", 0))
                .param("wrong", stats.getOrDefault("wrong", 0))
                .param("id", id)
                .update();
        return ResponseEntity.ok().build();
    }
}