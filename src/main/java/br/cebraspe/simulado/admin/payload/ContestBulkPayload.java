package br.cebraspe.simulado.admin.payload;

public record ContestBulkPayload(
        String name,
        String organ,
        String role,
        Integer year,
        String level,
        Boolean isDefault) {
}