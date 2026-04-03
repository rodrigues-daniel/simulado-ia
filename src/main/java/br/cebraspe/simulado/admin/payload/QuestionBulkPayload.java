package br.cebraspe.simulado.admin.payload;

import java.util.List;

public record QuestionBulkPayload(
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
        String difficulty) {
}