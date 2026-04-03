package br.cebraspe.simulado.ai.rag;

import java.time.LocalDateTime;

public record RagDocument(
        Long id,
        String name,
        String sourceType,
        Long topicId,
        Long contestId,
        String filePath,
        Integer totalChunks,
        String status,
        LocalDateTime createdAt) {
}