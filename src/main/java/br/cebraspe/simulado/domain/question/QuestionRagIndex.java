package br.cebraspe.simulado.domain.question;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionRagIndex(
        Long id,
        Long questionId,
        LocalDateTime indexedAt,
        String contentHash,
        Integer chunksCreated,
        List<Long> knowledgeIds,
        String status,
        String indexedBy) {
}