package br.cebraspe.simulado.domain.simulation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SimulationResult(
        Long id,
        Long simulationId,
        Integer totalQuestions,
        Integer answeredCount,
        Integer correctCount,
        Integer wrongCount,
        Integer skippedCount,
        BigDecimal grossScore,
        BigDecimal netScore,
        BigDecimal guessingTendencyPct,
        String riskLevel,
        List<String> keywordTrapsHit,
        String reportJson,
        LocalDateTime calculatedAt) {
}