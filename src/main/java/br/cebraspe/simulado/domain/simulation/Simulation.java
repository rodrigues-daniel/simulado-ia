package br.cebraspe.simulado.domain.simulation;

import java.time.LocalDateTime;

public record Simulation(
        Long id,
        Long contestId,
        String name,
        Integer totalQuestions,
        Integer timeLimitMin,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt) {
}