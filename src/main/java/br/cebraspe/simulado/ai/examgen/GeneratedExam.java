package br.cebraspe.simulado.ai.examgen;

import java.time.LocalDateTime;
import java.util.List;

public record GeneratedExam(
    Long   id,
    Long   templateId,
    String name,
    String status,
    Integer totalQuestions,
    String  generationModel,
    Boolean ragUsed,
    Boolean cacheUsed,
    LocalDateTime createdAt
) {}