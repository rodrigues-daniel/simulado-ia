package br.cebraspe.simulado.admin.payload;

import java.math.BigDecimal;

public record TopicBulkPayload(
        Long contestId,
        String name,
        String discipline,
        String lawReference,
        BigDecimal incidenceRate) {
}