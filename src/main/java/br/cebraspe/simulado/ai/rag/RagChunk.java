package br.cebraspe.simulado.ai.rag;

import java.time.LocalDateTime;
import java.util.UUID;

public record RagChunk(
                Long id,
                Long documentId,
                Integer chunkIndex,
                String content,
                String lawParagraph,
                Integer pageNumber,
                UUID vectorId,
                LocalDateTime createdAt) {
}