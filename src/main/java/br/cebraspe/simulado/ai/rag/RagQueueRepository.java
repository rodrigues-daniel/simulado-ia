package br.cebraspe.simulado.ai.rag;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RagQueueRepository {

    private final JdbcClient jdbcClient;

    public RagQueueRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public record QueueItem(
            Long id, Long documentId, String fileName,
            Integer chunkIndex, Integer totalChunks,
            String status, String errorMessage,
            LocalDateTime startedAt, LocalDateTime finishedAt,
            LocalDateTime createdAt
    ) {}

    public QueueItem enqueue(Long documentId, String fileName,
                              int chunkIndex, int totalChunks) {
        var id = jdbcClient.sql("""
                INSERT INTO rag_processing_queue
                    (document_id, file_name, chunk_index, total_chunks, status)
                VALUES (:documentId, :fileName, :chunkIndex, :totalChunks, 'PENDING')
                RETURNING id
                """)
                .param("documentId",  documentId)
                .param("fileName",    fileName)
                .param("chunkIndex",  chunkIndex)
                .param("totalChunks", totalChunks)
                .query(Long.class)
                .single();
        return findById(id).orElseThrow();
    }

    public Optional<QueueItem> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, document_id, file_name, chunk_index, total_chunks,
                       status, error_message, started_at, finished_at, created_at
                FROM rag_processing_queue WHERE id = :id
                """)
                .param("id", id)
                .query(QueueItem.class)
                .optional();
    }

    public List<QueueItem> findByDocumentId(Long documentId) {
        return jdbcClient.sql("""
                SELECT id, document_id, file_name, chunk_index, total_chunks,
                       status, error_message, started_at, finished_at, created_at
                FROM rag_processing_queue
                WHERE document_id = :documentId
                ORDER BY chunk_index
                """)
                .param("documentId", documentId)
                .query(QueueItem.class)
                .list();
    }

    public List<QueueItem> findPending() {
        return jdbcClient.sql("""
                SELECT id, document_id, file_name, chunk_index, total_chunks,
                       status, error_message, started_at, finished_at, created_at
                FROM rag_processing_queue
                WHERE status = 'PENDING'
                ORDER BY created_at
                """)
                .query(QueueItem.class)
                .list();
    }

    public List<QueueItem> findAll() {
        return jdbcClient.sql("""
                SELECT id, document_id, file_name, chunk_index, total_chunks,
                       status, error_message, started_at, finished_at, created_at
                FROM rag_processing_queue
                ORDER BY created_at DESC
                LIMIT 100
                """)
                .query(QueueItem.class)
                .list();
    }

    public void updateStatus(Long id, String status) {
        jdbcClient.sql("""
                UPDATE rag_processing_queue
                SET status     = :status,
                    started_at = CASE WHEN :status = 'PROCESSING'
                                      THEN NOW() ELSE started_at END,
                    finished_at= CASE WHEN :status IN ('COMPLETED','ERROR')
                                      THEN NOW() ELSE finished_at END
                WHERE id = :id
                """)
                .param("status", status)
                .param("id",     id)
                .update();
    }

    public void updateError(Long id, String error) {
        jdbcClient.sql("""
                UPDATE rag_processing_queue
                SET status      = 'ERROR',
                    error_message = :error,
                    finished_at = NOW()
                WHERE id = :id
                """)
                .param("error", error)
                .param("id",    id)
                .update();
    }

    public record QueueStats(
            long pending, long processing, long completed, long error
    ) {}

    public QueueStats getStats() {
        return jdbcClient.sql("""
                SELECT
                    COUNT(*) FILTER (WHERE status = 'PENDING')    AS pending,
                    COUNT(*) FILTER (WHERE status = 'PROCESSING') AS processing,
                    COUNT(*) FILTER (WHERE status = 'COMPLETED')  AS completed,
                    COUNT(*) FILTER (WHERE status = 'ERROR')      AS error
                FROM rag_processing_queue
                """)
                .query(QueueStats.class)
                .single();
    }
}