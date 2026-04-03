package br.cebraspe.simulado.domain.study;

import java.time.LocalDateTime;

public record StudySession(
                Long id,
                Long topicId,
                LocalDateTime startedAt,
                LocalDateTime finishedAt,
                Integer totalQuestions,
                Integer correctCount,
                Integer wrongCount,
                Integer skippedCount) {
}