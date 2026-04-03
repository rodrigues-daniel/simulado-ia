package br.cebraspe.simulado.ai.rag;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class RagRepository {

    private final JdbcClient jdbcClient;

    public RagRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public RagDocument saveDocument(RagDocument doc) {
        var id = jdbcClient.sql("""
                INSERT INTO rag_documents (name, source_type, topic_id, contest_id,
                                          file_path, total_chunks, status)
                VALUES (:name, :sourceType, :topicId, :contestId,
                        :filePath, 0, :status)
                RETURNING id
                """)
                .param("name", doc.name())
                .param("sourceType", doc.sourceType())
                .param("topicId", doc.topicId())
                .param("contestId", doc.contestId())
                .param("filePath", doc.filePath())
                .param("status", doc.status())
                .query(Long.class).single();
        return findDocumentById(id).orElseThrow();
    }

    public Optional<RagDocument> findDocumentById(Long id) {
        return jdbcClient.sql("""
                SELECT id, name, source_type, topic_id, contest_id,
                       file_path, total_chunks, status, created_at
                FROM rag_documents WHERE id = :id
                """)
                .param("id", id)
                .query(RagDocument.class)
                .optional();
    }

    public List<RagDocument> findAllDocuments() {
        return jdbcClient.sql("""
                SELECT id, name, source_type, topic_id, contest_id,
                       file_path, total_chunks, status, created_at
                FROM rag_documents ORDER BY created_at DESC
                """)
                .query(RagDocument.class)
                .list();
    }

    public void updateDocumentStatus(Long id, String status, Integer chunks) {
        jdbcClient.sql("""
                UPDATE rag_documents SET status = :status, total_chunks = :chunks
                WHERE id = :id
                """)
                .param("status", status)
                .param("chunks", chunks)
                .param("id", id)
                .update();
    }

    public void saveChunk(RagChunk chunk) {
        jdbcClient.sql("""
                INSERT INTO rag_chunks (document_id, chunk_index, content,
                                       law_paragraph, page_number)
                VALUES (:documentId, :chunkIndex, :content, :lawParagraph, :pageNumber)
                """)
                .param("documentId", chunk.documentId())
                .param("chunkIndex", chunk.chunkIndex())
                .param("content", chunk.content())
                .param("lawParagraph", chunk.lawParagraph())
                .param("pageNumber", chunk.pageNumber())
                .update();
    }
}