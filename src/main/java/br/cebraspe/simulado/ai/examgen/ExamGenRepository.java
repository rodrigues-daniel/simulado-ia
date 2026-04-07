package br.cebraspe.simulado.ai.examgen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.util.*;

@Repository
public class ExamGenRepository {

    private final JdbcClient  jdbcClient;
    private final ObjectMapper objectMapper;

    public ExamGenRepository(JdbcClient jdbcClient,
                              ObjectMapper objectMapper) {
        this.jdbcClient   = jdbcClient;
        this.objectMapper = objectMapper;
    }

    // ── Templates ────────────────────────────────────────────────────────

    public ExamGenTemplate saveTemplate(ExamGenTemplate t) {
        String discJson = toJson(t.disciplineConfig());
        String diffJson = toJson(t.difficultyDist());

        var id = jdbcClient.sql("""
                INSERT INTO exam_gen_templates (
                    name, description, contest_id, total_questions,
                    time_limit_min, discipline_config, difficulty_dist,
                    style_notes, is_active
                ) VALUES (
                    :name, :desc, :contestId, :total,
                    :time, :discConfig::jsonb, :diffDist::jsonb,
                    :style, :active
                )
                RETURNING id
                """)
                .param("name",       t.name())
                .param("desc",       t.description())
                .param("contestId",  t.contestId())
                .param("total",      t.totalQuestions())
                .param("time",       t.timeLimitMin())
                .param("discConfig", discJson)
                .param("diffDist",   diffJson)
                .param("style",      t.styleNotes())
                .param("active",     t.isActive() != null ? t.isActive() : true)
                .query(Long.class)
                .single();

        return findTemplateById(id).orElseThrow();
    }

    public Optional<ExamGenTemplate> findTemplateById(Long id) {
        return jdbcClient.sql("""
                SELECT id, name, description, contest_id, total_questions,
                       time_limit_min, discipline_config::text,
                       difficulty_dist::text, style_notes,
                       is_active, created_at
                FROM exam_gen_templates WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> mapTemplate(rs))
                .optional();
    }

    public List<ExamGenTemplate> findAllTemplates(Long contestId) {
        String filter = contestId != null
                ? "WHERE contest_id = :contestId " : "";
        var q = jdbcClient.sql(
                "SELECT id, name, description, contest_id, total_questions," +
                "       time_limit_min, discipline_config::text," +
                "       difficulty_dist::text, style_notes," +
                "       is_active, created_at " +
                "FROM exam_gen_templates " + filter +
                "ORDER BY created_at DESC");
        if (contestId != null) q = q.param("contestId", contestId);
        return q.query((rs, n) -> mapTemplate(rs)).list();
    }

    public void deleteTemplate(Long id) {
        jdbcClient.sql("UPDATE exam_gen_templates SET is_active=FALSE WHERE id=:id")
                .param("id", id).update();
    }

    // ── Exams ────────────────────────────────────────────────────────────

    public GeneratedExam createExam(Long templateId, String name) {
        var id = jdbcClient.sql("""
                INSERT INTO generated_exams
                    (template_id, name, status)
                VALUES (:tid, :name, 'GENERATING')
                RETURNING id
                """)
                .param("tid",  templateId)
                .param("name", name)
                .query(Long.class)
                .single();
        return findExamById(id).orElseThrow();
    }

    public Optional<GeneratedExam> findExamById(Long id) {
        return jdbcClient.sql("""
                SELECT id, template_id, name, status, total_questions,
                       generation_model, rag_used, cache_used, created_at
                FROM generated_exams WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new GeneratedExam(
                        rs.getLong("id"),
                        rs.getLong("template_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("total_questions"),
                        rs.getString("generation_model"),
                        rs.getBoolean("rag_used"),
                        rs.getBoolean("cache_used"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ))
                .optional();
    }

    public List<GeneratedExam> findExamsByTemplate(Long templateId) {
        return jdbcClient.sql("""
                SELECT id, template_id, name, status, total_questions,
                       generation_model, rag_used, cache_used, created_at
                FROM generated_exams
                WHERE template_id = :tid
                ORDER BY created_at DESC
                """)
                .param("tid", templateId)
                .query((rs, n) -> new GeneratedExam(
                        rs.getLong("id"),
                        rs.getLong("template_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("total_questions"),
                        rs.getString("generation_model"),
                        rs.getBoolean("rag_used"),
                        rs.getBoolean("cache_used"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ))
                .list();
    }

    public List<GeneratedExam> findAllExams() {
        return jdbcClient.sql("""
                SELECT id, template_id, name, status, total_questions,
                       generation_model, rag_used, cache_used, created_at
                FROM generated_exams
                ORDER BY created_at DESC
                LIMIT 50
                """)
                .query((rs, n) -> new GeneratedExam(
                        rs.getLong("id"),
                        rs.getLong("template_id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("total_questions"),
                        rs.getString("generation_model"),
                        rs.getBoolean("rag_used"),
                        rs.getBoolean("cache_used"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ))
                .list();
    }

    public void updateExamStatus(Long examId, String status,
                                  int totalQuestions,
                                  boolean ragUsed, boolean cacheUsed) {
        jdbcClient.sql("""
                UPDATE generated_exams
                SET status          = :status,
                    total_questions = :total,
                    rag_used        = :rag,
                    cache_used      = :cache
                WHERE id = :id
                """)
                .param("status", status)
                .param("total",  totalQuestions)
                .param("rag",    ragUsed)
                .param("cache",  cacheUsed)
                .param("id",     examId)
                .update();
    }

    // ── Questions ────────────────────────────────────────────────────────

    public Long saveExamQuestion(Long examId, GeneratedExamQuestion q) {
        return jdbcClient.sql("""
                INSERT INTO generated_exam_questions (
                    exam_id, question_id, order_number, statement,
                    correct_answer, explanation, discipline, topic,
                    difficulty, law_reference, trap_keywords,
                    rag_score, from_cache
                ) VALUES (
                    :examId, :questionId, :order, :stmt,
                    :answer, :expl, :disc, :topic,
                    :diff, :law, :traps::text[],
                    :ragScore, :cache
                )
                RETURNING id
                """)
                .param("examId",     examId)
                .param("questionId", q.questionId())
                .param("order",      q.orderNumber())
                .param("stmt",       q.statement())
                .param("answer",     q.correctAnswer())
                .param("expl",       q.explanation())
                .param("disc",       q.discipline())
                .param("topic",      q.topic())
                .param("diff",       q.difficulty())
                .param("law",        q.lawReference())
                .param("traps",      q.trapKeywords() != null
                        ? q.trapKeywords().toArray(String[]::new)
                        : new String[]{})
                .param("ragScore",   q.ragScore())
                .param("cache",      q.fromCache() != null
                        ? q.fromCache() : false)
                .query(Long.class)
                .single();
    }

    public List<GeneratedExamQuestion> findExamQuestions(Long examId) {
        return jdbcClient.sql("""
                SELECT id, exam_id, question_id, order_number, statement,
                       correct_answer, explanation, discipline, topic,
                       difficulty, law_reference, trap_keywords,
                       rag_score, from_cache, created_at
                FROM generated_exam_questions
                WHERE exam_id = :examId
                ORDER BY order_number
                """)
                .param("examId", examId)
                .query((rs, n) -> {
                    Array trapArr = rs.getArray("trap_keywords");
                    List<String> traps = trapArr != null
                            ? Arrays.asList((String[]) trapArr.getArray())
                            : List.of();
                    return new GeneratedExamQuestion(
                            rs.getLong("id"),
                            rs.getLong("exam_id"),
                            rs.getObject("question_id", Long.class),
                            rs.getInt("order_number"),
                            rs.getString("statement"),
                            rs.getBoolean("correct_answer"),
                            rs.getString("explanation"),
                            rs.getString("discipline"),
                            rs.getString("topic"),
                            rs.getString("difficulty"),
                            rs.getString("law_reference"),
                            traps,
                            rs.getObject("rag_score", BigDecimal.class),
                            rs.getBoolean("from_cache"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                    );
                })
                .list();
    }

    public void saveAnswerKey(Long examId, int order, boolean answer) {
        jdbcClient.sql("""
                INSERT INTO generated_exam_answers
                    (exam_id, question_order, correct_answer)
                VALUES (:examId, :order, :answer)
                ON CONFLICT (exam_id, question_order) DO NOTHING
                """)
                .param("examId", examId)
                .param("order",  order)
                .param("answer", answer)
                .update();
    }

    public List<Map<String, Object>> getAnswerKey(Long examId) {
        return jdbcClient.sql("""
                SELECT question_order, correct_answer
                FROM generated_exam_answers
                WHERE exam_id = :examId
                ORDER BY question_order
                """)
                .param("examId", examId)
                .query((ResultSet rs, int n) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("order", rs.getInt("question_order"));
                    map.put("answer", rs.getBoolean("correct_answer"));
                    return map;
                })
                .list();
    }

    // ── Mappers ──────────────────────────────────────────────────────────

    private ExamGenTemplate mapTemplate(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        List<ExamGenTemplate.DisciplineConfig> disciplines = List.of();
        ExamGenTemplate.DifficultyDist diff = new ExamGenTemplate.DifficultyDist(30, 50, 20);
        try {
            String discJson = rs.getString("discipline_config");
            String diffJson = rs.getString("difficulty_dist");
            if (discJson != null) {
                disciplines = objectMapper.readValue(discJson,
                        new TypeReference<>() {});
            }
            if (diffJson != null) {
                diff = objectMapper.readValue(diffJson,
                        ExamGenTemplate.DifficultyDist.class);
            }
        } catch (Exception e) { /* usa defaults */ }

        return new ExamGenTemplate(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("contest_id", Long.class),
                rs.getInt("total_questions"),
                rs.getInt("time_limit_min"),
                disciplines,
                diff,
                rs.getString("style_notes"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }
}