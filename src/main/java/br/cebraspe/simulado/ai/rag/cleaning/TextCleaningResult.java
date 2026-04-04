package br.cebraspe.simulado.ai.rag.cleaning;

import java.util.List;

public record TextCleaningResult(
    String originalText,
    String cleanedText,
    CleaningStrategy strategyUsed,
    double confidenceScore,      // 0.0 a 1.0
    boolean requiresManualReview,
    List<CleaningIssue> issuesFound,
    CleaningStats stats
) {
    public record CleaningIssue(
        String type,
        String description,
        int occurrences
    ) {}

    public record CleaningStats(
        int originalChars,
        int cleanedChars,
        int removedChars,
        int duplicateLinesRemoved,
        int stopWordsRemoved,
        int specialCharsNormalized,
        double reductionPct
    ) {}
}