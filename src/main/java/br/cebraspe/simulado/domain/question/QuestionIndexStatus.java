package br.cebraspe.simulado.domain.question;

import java.time.LocalDateTime;

public record QuestionIndexStatus(
        Long questionId,
        String statement,
        Boolean correctAnswer,
        String source,
        LocalDateTime questionCreatedAt,
        LocalDateTime questionUpdatedAt,
        Long indexId,
        LocalDateTime indexedAt,
        String contentHash,
        Integer chunksCreated,
        String indexStatus // NOT_INDEXED | INDEXED | OUTDATED
) {
}