package br.cebraspe.simulado.domain.essay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class EssaySkeletonRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public EssaySkeletonRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public List<EssaySkeleton> findByTopicId(Long topicId) {
        return jdbcClient.sql("""
                SELECT id, topic_id, contest_id, title, introduction,
                       body_points, conclusion, mandatory_keywords,
                       banca_tips, word_limit, generated_by_ai, created_at
                FROM essay_skeletons
                WHERE topic_id = :topicId
                ORDER BY created_at DESC
                """)
                .param("topicId", topicId)
                .query((rs, rowNum) -> mapSkeleton(rs))
                .list();
    }

    public List<EssaySkeleton> findByContestId(Long contestId) {
        return jdbcClient.sql("""
                SELECT id, topic_id, contest_id, title, introduction,
                       body_points, conclusion, mandatory_keywords,
                       banca_tips, word_limit, generated_by_ai, created_at
                FROM essay_skeletons
                WHERE contest_id = :contestId
                ORDER BY created_at DESC
                """)
                .param("contestId", contestId)
                .query((rs, rowNum) -> mapSkeleton(rs))
                .list();
    }

    public Optional<EssaySkeleton> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, topic_id, contest_id, title, introduction,
                       body_points, conclusion, mandatory_keywords,
                       banca_tips, word_limit, generated_by_ai, created_at
                FROM essay_skeletons
                WHERE id = :id
                """)
                .param("id", id)
                .query((rs, rowNum) -> mapSkeleton(rs))
                .optional();
    }

    public EssaySkeleton save(EssaySkeleton skeleton) {
        if (skeleton.id() == null) {
            var id = jdbcClient.sql("""
                    INSERT INTO essay_skeletons (topic_id, contest_id, title,
                        introduction, body_points, conclusion, mandatory_keywords,
                        banca_tips, word_limit, generated_by_ai)
                    VALUES (:topicId, :contestId, :title,
                            :introduction, :bodyPoints::jsonb, :conclusion,
                            :mandatoryKeywords::text[], :bancaTips,
                            :wordLimit, :generatedByAi)
                    RETURNING id
                    """)
                    .param("topicId", skeleton.topicId())
                    .param("contestId", skeleton.contestId())
                    .param("title", skeleton.title())
                    .param("introduction", skeleton.introduction())
                    .param("bodyPoints", toJson(skeleton.bodyPoints()))
                    .param("conclusion", skeleton.conclusion())
                    .param("mandatoryKeywords", skeleton.mandatoryKeywords() != null
                            ? skeleton.mandatoryKeywords().toArray(String[]::new)
                            : new String[] {})
                    .param("bancaTips", skeleton.bancaTips())
                    .param("wordLimit", skeleton.wordLimit())
                    .param("generatedByAi", skeleton.generatedByAi())
                    .query(Long.class)
                    .single();
            return findById(id).orElseThrow();
        }

        jdbcClient.sql("""
                UPDATE essay_skeletons SET
                    title = :title,
                    introduction = :introduction,
                    body_points = :bodyPoints::jsonb,
                    conclusion = :conclusion,
                    mandatory_keywords = :mandatoryKeywords::text[],
                    banca_tips = :bancaTips,
                    word_limit = :wordLimit
                WHERE id = :id
                """)
                .param("title", skeleton.title())
                .param("introduction", skeleton.introduction())
                .param("bodyPoints", toJson(skeleton.bodyPoints()))
                .param("conclusion", skeleton.conclusion())
                .param("mandatoryKeywords", skeleton.mandatoryKeywords() != null
                        ? skeleton.mandatoryKeywords().toArray(String[]::new)
                        : new String[] {})
                .param("bancaTips", skeleton.bancaTips())
                .param("wordLimit", skeleton.wordLimit())
                .param("id", skeleton.id())
                .update();

        return findById(skeleton.id()).orElseThrow();
    }

    public void deleteById(Long id) {
        jdbcClient.sql("DELETE FROM essay_skeletons WHERE id = :id")
                .param("id", id)
                .update();
    }

    // ── Mappers ──────────────────────────────────────────────────────────

    private EssaySkeleton mapSkeleton(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> bodyPoints = parseJsonArray(rs.getString("body_points"));
        List<String> mandatoryKeywords = parseStringArray(rs.getArray("mandatory_keywords"));

        return new EssaySkeleton(
                rs.getLong("id"),
                rs.getObject("topic_id", Long.class),
                rs.getObject("contest_id", Long.class),
                rs.getString("title"),
                rs.getString("introduction"),
                bodyPoints,
                rs.getString("conclusion"),
                mandatoryKeywords,
                rs.getString("banca_tips"),
                rs.getInt("word_limit"),
                rs.getBoolean("generated_by_ai"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank())
            return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseStringArray(java.sql.Array sqlArray) {
        if (sqlArray == null)
            return List.of();
        try {
            return java.util.Arrays.asList((String[]) sqlArray.getArray());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(List<String> list) {
        if (list == null)
            return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}