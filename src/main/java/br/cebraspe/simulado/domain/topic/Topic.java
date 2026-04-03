package br.cebraspe.simulado.domain.topic;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Topic(
        Long id,
        Long contestId,
        String name,
        String discipline,
        String lawReference,
        BigDecimal incidenceRate,
        Boolean isPriority,
        Boolean isHidden,
        LocalDateTime createdAt) {
}