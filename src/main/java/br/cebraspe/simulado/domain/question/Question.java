package br.cebraspe.simulado.domain.question;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Question(
        Long id,
        Long topicId,
        Long contestId,
        String statement,
        Boolean correctAnswer,
        String lawParagraph,
        String lawReference,
        String explanation,
        String professorTip,
        List<String> trapKeywords,
        Integer year,
        String source,
        String difficulty,
        // ── Campos IA (V7) ──────────────────────────────
        Boolean iaReviewed,
        Boolean iaApproved,
        BigDecimal iaConfidence,
        String reviewNote,
        LocalDateTime reviewedAt,
        // ────────────────────────────────────────────────
        LocalDateTime createdAt
) {}