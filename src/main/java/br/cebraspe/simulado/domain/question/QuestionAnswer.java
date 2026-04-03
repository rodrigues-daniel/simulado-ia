package br.cebraspe.simulado.domain.question;

import java.time.LocalDateTime;

public record QuestionAnswer(
        Long id,
        Long sessionId,
        Long questionId,
        Boolean userAnswer,
        Boolean isCorrect,
        LocalDateTime answeredAt,
        Integer timeSpentMs) {
}