package br.cebraspe.simulado.ai.examgen;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GeneratedExamQuestion(
    Long    id,
    Long    examId,
    Long    questionId,
    Integer orderNumber,
    String  statement,
    Boolean correctAnswer,
    String  explanation,
    String  discipline,
    String  topic,
    String  difficulty,
    String  lawReference,
    List<String> trapKeywords,
    BigDecimal   ragScore,
    Boolean      fromCache,
    LocalDateTime createdAt
) {}