package br.cebraspe.simulado.ai.examgen;

import java.time.LocalDateTime;
import java.util.List;

public record ExamGenTemplate(
    Long   id,
    String name,
    String description,
    Long   contestId,
    Integer totalQuestions,
    Integer timeLimitMin,
    List<DisciplineConfig> disciplineConfig,
    DifficultyDist         difficultyDist,
    String styleNotes,
    Boolean isActive,
    LocalDateTime createdAt
) {
    public record DisciplineConfig(
        String discipline,
        String lawReference,
        List<String> topics,
        Integer questionCount,
        String extraContext
    ) {}

    public record DifficultyDist(
        int facil,
        int medio,
        int dificil
    ) {}
}