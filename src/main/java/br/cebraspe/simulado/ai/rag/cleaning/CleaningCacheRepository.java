package br.cebraspe.simulado.ai.rag.cleaning;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class CleaningCacheRepository {

    private final JdbcClient jdbcClient;

    public CleaningCacheRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void saveCleanedContent(Long documentId, String cleanedText,
                                    String strategy, double confidence,
                                    boolean manuallyEdited) {
        jdbcClient.sql("""
                INSERT INTO rag_document_cleaning
                    (document_id, cleaned_content, strategy_used,
                     confidence_score, manually_edited)
                VALUES (:docId, :content, :strategy, :confidence, :manual)
                ON CONFLICT (document_id) DO UPDATE SET
                    cleaned_content  = EXCLUDED.cleaned_content,
                    strategy_used    = EXCLUDED.strategy_used,
                    confidence_score = EXCLUDED.confidence_score,
                    manually_edited  = EXCLUDED.manually_edited,
                    updated_at       = NOW()
                """)
                .param("docId",      documentId)
                .param("content",    cleanedText)
                .param("strategy",   strategy)
                .param("confidence", confidence)
                .param("manual",     manuallyEdited)
                .update();
    }

    public Optional<CleanedDoc> findByDocumentId(Long documentId) {
        return jdbcClient.sql("""
                SELECT document_id, cleaned_content, strategy_used,
                       confidence_score, manually_edited, updated_at
                FROM rag_document_cleaning
                WHERE document_id = :docId
                """)
                .param("docId", documentId)
                .query(CleanedDoc.class)
                .optional();
    }

    public record CleanedDoc(
            Long documentId,
            String cleanedContent,
            String strategyUsed,
            double confidenceScore,
            boolean manuallyEdited,
            java.time.LocalDateTime updatedAt
    ) {}
}