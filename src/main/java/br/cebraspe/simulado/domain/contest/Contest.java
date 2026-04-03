package br.cebraspe.simulado.domain.contest;

import java.time.LocalDateTime;

public record Contest(
                Long id,
                String name,
                String organ,
                String role,
                Integer year,
                String level,
                Boolean isDefault,
                LocalDateTime createdAt) {
}