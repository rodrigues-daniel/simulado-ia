package br.cebraspe.simulado.domain.pareto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ParetoAnalysis(
        Long id,
        Long topicId,
        Long contestId,
        BigDecimal incidenceRate,
        Integer questionCount,
        BigDecimal avgDifficulty,
        Boolean isParetoTop,
        LocalDateTime lastUpdated,
        String topicName,
        String discipline) {
}