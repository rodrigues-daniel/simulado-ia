package br.cebraspe.simulado.domain.question;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class QuestionRagIndexRepository {

    private final JdbcClient jdbcClient;

    public QuestionRagIndexRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // ── Busca status por questão ────────────────────────────────────────
    public Optional<QuestionRagIndex> findByQuestionId(Long questionId) {
        return jdbcClient.sql("""
                SELECT id, question_id, indexed_at, content_hash,
                       chunks_created, knowledge_ids, status, indexed_by
                FROM question_rag_index
                WHERE question_id = :questionId
                """)
                .param("questionId", questionId)
                .query((rs, n) -> mapIndex(rs))
                .optional();
    }

    // ── Lista status de todas as questões de um tópico ──────────────────
    public List<QuestionIndexStatus> findStatusByTopic(Long topicId) {
        return jdbcClient.sql("""
                SELECT question_id, statement, correct_answer, source,
                       question_created_at, question_updated_at,
                       index_id, indexed_at, content_hash,
                       chunks_created, computed_status AS index_status
                FROM vw_questions_index_status
                WHERE topic_id = :topicId
                ORDER BY computed_status DESC, question_created_at DESC
                """)
                .param("topicId", topicId)
                .query((rs, n) -> mapStatus(rs))
                .list();
    }

    // ── Lista status de todas as questões de um concurso ───────────────
    public List<QuestionIndexStatus> findStatusByContest(Long contestId) {
        return jdbcClient.sql("""
                SELECT v.question_id, v.statement, v.correct_answer,
                       v.source, v.question_created_at, v.question_updated_at,
                       v.index_id, v.indexed_at, v.content_hash,
                       v.chunks_created, v.computed_status AS index_status
                FROM vw_questions_index_status v
                JOIN topics t ON v.topic_id = t.id
                WHERE t.contest_id = :contestId
                ORDER BY v.computed_status DESC, v.question_created_at DESC
                """)
                .param("contestId", contestId)
                .query((rs, n) -> mapStatus(rs))
                .list();
    }

    // ── Resumo de indexação por concurso ────────────────────────────────
    public IndexSummary getSummaryByContest(Long contestId) {
        return jdbcClient.sql("""
                SELECT
                    COUNT(*)                                             AS total,
                    COUNT(*) FILTER (WHERE computed_status = 'INDEXED')     AS indexed,
                    COUNT(*) FILTER (WHERE computed_status = 'NOT_INDEXED') AS not_indexed,
                    COUNT(*) FILTER (WHERE computed_status = 'OUTDATED')    AS outdated
                FROM vw_questions_index_status v
                JOIN topics t ON v.topic_id = t.id
                WHERE t.contest_id = :contestId
                """)
                .param("contestId", contestId)
                .query((rs, n) -> new IndexSummary(
                        rs.getLong("total"),
                        rs.getLong("indexed"),
                        rs.getLong("not_indexed"),
                        rs.getLong("outdated")))
                .single();
    }

    // ── Questões pendentes de indexação ─────────────────────────────────
    public List<QuestionIndexStatus> findPending(Long contestId) {
        return jdbcClient.sql("""
                SELECT v.question_id, v.statement, v.correct_answer,
                       v.source, v.question_created_at, v.question_updated_at,
                       v.index_id, v.indexed_at, v.content_hash,
                       v.chunks_created, v.computed_status AS index_status
                FROM vw_questions_index_status v
                JOIN topics t ON v.topic_id = t.id
                WHERE t.contest_id = :contestId
                  AND v.computed_status IN ('NOT_INDEXED', 'OUTDATED')
                ORDER BY v.question_created_at DESC
                """)
                .param("contestId", contestId)
                .query((rs, n) -> mapStatus(rs))
                .list();
    }

    // ── Verifica se questão pode ser indexada ───────────────────────────
    public boolean canIndex(Long questionId, String newHash) {
        var existing = findByQuestionId(questionId);
        if (existing.isEmpty())
            return true; // nunca indexada
        // Permite re-indexar apenas se hash mudou (conteúdo modificado)
        return !existing.get().contentHash().equals(newHash);
    }

    // ── Salva registro de indexação ─────────────────────────────────────
    public QuestionRagIndex saveIndex(Long questionId, String contentHash,
            int chunksCreated, List<Long> knowledgeIds,
            String indexedBy) {
        jdbcClient.sql("""
                INSERT INTO question_rag_index
                    (question_id, content_hash, chunks_created,
                     knowledge_ids, status, indexed_by)
                VALUES
                    (:questionId, :hash, :chunks,
                     :knowledgeIds::bigint[], 'INDEXED', :indexedBy)
                ON CONFLICT (question_id) DO UPDATE SET
                    indexed_at    = NOW(),
                    content_hash  = EXCLUDED.content_hash,
                    chunks_created= EXCLUDED.chunks_created,
                    knowledge_ids = EXCLUDED.knowledge_ids,
                    status        = 'INDEXED',
                    indexed_by    = EXCLUDED.indexed_by
                """)
                .param("questionId", questionId)
                .param("hash", contentHash)
                .param("chunks", chunksCreated)
                .param("knowledgeIds", knowledgeIds != null
                        ? knowledgeIds.toArray(Long[]::new)
                        : new Long[] {})
                .param("indexedBy", indexedBy)
                .update();

        return findByQuestionId(questionId).orElseThrow();
    }

    // ── Remove registro (permite re-indexação forçada) ──────────────────
    public void removeIndex(Long questionId) {
        jdbcClient.sql("""
                DELETE FROM question_rag_index WHERE question_id = :questionId
                """)
                .param("questionId", questionId)
                .update();
    }

    // ── Mappers ──────────────────────────────────────────────────────────

    private QuestionRagIndex mapIndex(ResultSet rs) throws SQLException {
        Array arr = rs.getArray("knowledge_ids");
        List<Long> ids = arr != null
                ? Arrays.stream((Long[]) arr.getArray()).toList()
                : List.of();
        Timestamp indexedAt = rs.getTimestamp("indexed_at");
        return new QuestionRagIndex(
                rs.getLong("id"),
                rs.getLong("question_id"),
                indexedAt != null ? indexedAt.toLocalDateTime() : null,
                rs.getString("content_hash"),
                rs.getInt("chunks_created"),
                ids,
                rs.getString("status"),
                rs.getString("indexed_by"));
    }

    private QuestionIndexStatus mapStatus(ResultSet rs) throws SQLException {
        Timestamp qCreated = rs.getTimestamp("question_created_at");
        Timestamp qUpdated = rs.getTimestamp("question_updated_at");
        Timestamp indexedAt = rs.getTimestamp("indexed_at");
        return new QuestionIndexStatus(
                rs.getLong("question_id"),
                rs.getString("statement"),
                rs.getBoolean("correct_answer"),
                rs.getString("source"),
                qCreated != null ? qCreated.toLocalDateTime() : null,
                qUpdated != null ? qUpdated.toLocalDateTime() : null,
                rs.getObject("index_id", Long.class),
                indexedAt != null ? indexedAt.toLocalDateTime() : null,
                rs.getString("content_hash"),
                rs.getObject("chunks_created", Integer.class),
                rs.getString("index_status"));
    }

    public record IndexSummary(
            long total, long indexed, long notIndexed, long outdated) {
    }
}