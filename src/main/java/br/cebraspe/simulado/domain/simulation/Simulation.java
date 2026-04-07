package br.cebraspe.simulado.domain.simulation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Simulation(
        Long          id,
        Long          contestId,
        String        name,
        Integer       totalQuestions,
        Integer       timeLimitMin,
        String        status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        // Campos V16 — score real Cebraspe
        String        modality,
        Integer       totalVacancies,
        Integer       quotaVacancies,
        BigDecimal    cutScoreAmpla,
        BigDecimal    cutScoreQuota,
        BigDecimal    pointsCorrect,
        BigDecimal    pointsWrong
) {}