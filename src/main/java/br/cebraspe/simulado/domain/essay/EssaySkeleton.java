package br.cebraspe.simulado.domain.essay;

import java.time.LocalDateTime;
import java.util.List;

public record EssaySkeleton(
        Long id,
        Long topicId,
        Long contestId,
        String title,
        String introduction,
        List<String> bodyPoints,
        String conclusion,
        List<String> mandatoryKeywords,
        String bancaTips,
        Integer wordLimit,
        Boolean generatedByAi,
        LocalDateTime createdAt) {
}