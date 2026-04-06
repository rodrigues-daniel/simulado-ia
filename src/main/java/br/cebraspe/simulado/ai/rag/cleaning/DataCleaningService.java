package br.cebraspe.simulado.ai.rag.cleaning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DataCleaningService {

    private static final Logger log =
            LoggerFactory.getLogger(DataCleaningService.class);

    // Limiar abaixo do qual exige revisão manual
    private static final double MANUAL_REVIEW_THRESHOLD = 0.65;

    // Stop-words do português jurídico/administrativo
    // (mantém termos técnicos importantes)
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "o", "as", "os", "um", "uma", "uns", "umas",
        "de", "do", "da", "dos", "das", "em", "no", "na",
        "nos", "nas", "por", "para", "com", "sem", "sob",
        "que", "se", "ou", "e", "mas", "pois", "porque",
        "como", "quando", "onde", "qual", "quais",
        "ao", "aos", "à", "às", "pelo", "pela", "pelos", "pelas",
        "este", "esta", "estes", "estas", "esse", "essa",
        "isso", "isto", "aquele", "aquela", "aquilo",
        "ser", "estar", "ter", "haver", "sendo", "tendo",
        "sido", "estado", "foi", "era", "será", "seria"
    );

    // ── Detecta estratégia pelo tipo e conteúdo ─────────────────────────
    public CleaningStrategy detectStrategy(String fileName, String content) {
        String ext     = getExtension(fileName).toLowerCase();
        String sample  = content.length() > 2000
                ? content.substring(0, 2000) : content;

        if ("csv".equals(ext)) {
            return CleaningStrategy.CSV_STRUCTURED;
        }

        if ("txt".equals(ext)) {
            // Detecta se parece banco de questões
            boolean hasQuestions = sample.matches("(?s).*\\d+[.)]\\s+.{20,}.*")
                    || sample.contains("CERTO") || sample.contains("ERRADO")
                    || sample.contains("Gabarito") || sample.contains("gabarito");
            return hasQuestions
                    ? CleaningStrategy.TXT_QUESTIONS
                    : CleaningStrategy.TXT_PLAIN;
        }

        if ("pdf".equals(ext) || content.length() > 500) {
            // Detecta se é documento jurídico/legal
            boolean isLegal = containsLegalTerms(sample);
            boolean isAcademic = containsAcademicTerms(sample);

            if (isLegal)    return CleaningStrategy.PDF_LEGAL;
            if (isAcademic) return CleaningStrategy.PDF_ACADEMIC;
        }

        return CleaningStrategy.GENERIC;
    }

    // ── Aplica limpeza com a estratégia detectada ───────────────────────
    public TextCleaningResult clean(String content, String fileName) {
        var strategy = detectStrategy(fileName, content);
        return cleanWithStrategy(content, fileName, strategy);
    }

    // ── Aplica limpeza com estratégia específica ────────────────────────
    public TextCleaningResult cleanWithStrategy(String content,
                                                 String fileName,
                                                 CleaningStrategy strategy) {
        List<TextCleaningResult.CleaningIssue> issues = new ArrayList<>();
        String cleaned = content;
        int stopWordsRemoved        = 0;
        int specialCharsNormalized  = 0;
        int duplicateLinesRemoved   = 0;

        try {
            // ── 1. Normalização Unicode ─────────────────────────────────
            cleaned = normalizeUnicode(cleaned);

            // ── 2. Remove caracteres de controle ────────────────────────
            int beforeControl = cleaned.length();
            cleaned = removeControlChars(cleaned);
            if (cleaned.length() < beforeControl) {
                issues.add(new TextCleaningResult.CleaningIssue(
                    "CONTROL_CHARS",
                    "Caracteres de controle removidos",
                    beforeControl - cleaned.length()
                ));
                specialCharsNormalized += beforeControl - cleaned.length();
            }

            // ── 3. Estratégia específica ────────────────────────────────
            switch (strategy) {

                case PDF_LEGAL -> {
                    cleaned = cleanPdfLegal(cleaned, issues);
                    specialCharsNormalized += countPattern(content,
                            PATTERN_PAGE_ARTIFACTS);
                }

                case PDF_ACADEMIC -> {
                    cleaned = cleanPdfAcademic(cleaned, issues);
                }

                case CSV_STRUCTURED -> {
                    cleaned = cleanCsv(cleaned, issues);
                }

                case TXT_QUESTIONS -> {
                    cleaned = cleanTxtQuestions(cleaned, issues);
                }

                case TXT_PLAIN -> {
                    cleaned = cleanTxtPlain(cleaned, issues);
                }

                default -> {
                    cleaned = cleanGeneric(cleaned, issues);
                }
            }

            // ── 4. Remove linhas duplicadas ──────────────────────────────
            int beforeDedup = countLines(cleaned);
            cleaned = removeDuplicateLines(cleaned);
            duplicateLinesRemoved = beforeDedup - countLines(cleaned);
            if (duplicateLinesRemoved > 0) {
                issues.add(new TextCleaningResult.CleaningIssue(
                    "DUPLICATE_LINES",
                    "Linhas duplicadas removidas",
                    duplicateLinesRemoved
                ));
            }

            // ── 5. Remove stop-words APENAS em chunks pequenos ──────────
            // Não aplica em textos longos para preservar contexto
            if (content.length() < 5000
                    && strategy != CleaningStrategy.CSV_STRUCTURED) {
                int beforeSW = countWords(cleaned);
                cleaned = removeStopWords(cleaned);
                stopWordsRemoved = beforeSW - countWords(cleaned);
                if (stopWordsRemoved > 0) {
                    issues.add(new TextCleaningResult.CleaningIssue(
                        "STOP_WORDS",
                        "Stop-words removidas (preserva termos jurídicos)",
                        stopWordsRemoved
                    ));
                }
            }

            // ── 6. Normalização final de espaços ─────────────────────────
            cleaned = normalizeWhitespace(cleaned).trim();

        } catch (Exception e) {
            log.warn("Erro durante limpeza de '{}': {}", fileName, e.getMessage());
            // Retorna texto parcialmente limpo com confiança baixa
            return buildResult(content, cleaned, strategy, 0.40,
                    true, issues, stopWordsRemoved,
                    specialCharsNormalized, duplicateLinesRemoved);
        }

        // ── Calcula confiança ───────────────────────────────────────────
        double confidence = calculateConfidence(
                content, cleaned, strategy, issues);

        boolean requiresManual = confidence < MANUAL_REVIEW_THRESHOLD
                || hasHighRiskIssues(issues);

        return buildResult(content, cleaned, strategy, confidence,
                requiresManual, issues, stopWordsRemoved,
                specialCharsNormalized, duplicateLinesRemoved);
    }

    // ── Limpeza PDF Legal ────────────────────────────────────────────────
    private static final Pattern PATTERN_PAGE_ARTIFACTS =
            Pattern.compile("(?m)^\\s*Página\\s+\\d+\\s*$|" +
                            "^\\s*\\d+\\s*$|" +        // numeração isolada
                            "^\\s*[-–—]+\\s*$|" +      // linhas separadoras
                            "Fl\\.?\\s*\\d+|" +         // "Fl. 42"
                            "fls?\\.?\\s*\\d+",         // "fls. 42"
                            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_HEADER_FOOTER =
            Pattern.compile("(?m)^.{0,80}(TRIBUNAL|MINISTÉRIO|SECRETARIA|" +
                            "PODER JUDICIÁRIO|DIÁRIO OFICIAL).{0,80}$",
                            Pattern.CASE_INSENSITIVE);

    private String cleanPdfLegal(String text,
                                  List<TextCleaningResult.CleaningIssue> issues) {
        String result = text;

        // Remove artefatos de página
        int before = countLines(result);
        result = PATTERN_PAGE_ARTIFACTS.matcher(result).replaceAll("");
        int removed = before - countLines(result);
        if (removed > 0) {
            issues.add(new TextCleaningResult.CleaningIssue(
                "PDF_PAGE_ARTIFACTS",
                "Artefatos de paginação removidos (números de página, separadores)",
                removed
            ));
        }

        // Remove cabeçalhos/rodapés repetitivos
        result = PATTERN_HEADER_FOOTER.matcher(result).replaceAll("");

        // Normaliza citações legais: "Art.1o" → "Art. 1º"
        result = result.replaceAll("(?i)art\\.\\s*(\\d+)", "Art. $1");
        result = result.replaceAll("§\\s*(\\d+)", "§ $1");
        result = result.replaceAll("(?i)inc\\.\\s*(\\w+)", "Inc. $1");

        // Reconstrói parágrafos fragmentados por quebra de linha do PDF
        result = rejoinBrokenParagraphs(result);

        return result;
    }

    // ── Limpeza PDF Acadêmico ────────────────────────────────────────────
    private static final Pattern PATTERN_BIBLIOGRAPHY =
            Pattern.compile("(?m)^\\s*REFERÊNCIAS?\\s*BIBLIOGRÁFICAS?.*$" +
                            "|^\\s*BIBLIOGRAPHY.*$", Pattern.CASE_INSENSITIVE);

    private String cleanPdfAcademic(String text,
                                     List<TextCleaningResult.CleaningIssue> issues) {
        String result = text;

        // Remove seção de referências bibliográficas
        var matcher = PATTERN_BIBLIOGRAPHY.matcher(result);
        if (matcher.find()) {
            result = result.substring(0, matcher.start());
            issues.add(new TextCleaningResult.CleaningIssue(
                "BIBLIOGRAPHY_REMOVED",
                "Seção de referências bibliográficas removida",
                1
            ));
        }

        // Remove numeração de seções isoladas
        result = result.replaceAll("(?m)^\\s*\\d+(\\.\\d+)*\\s*$", "");

        // Reconstrói parágrafos
        result = rejoinBrokenParagraphs(result);

        return result;
    }

    // ── Limpeza CSV ──────────────────────────────────────────────────────
    private String cleanCsv(String text,
                              List<TextCleaningResult.CleaningIssue> issues) {
        String[] lines  = text.split("\n");
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int emptyRemoved = 0, dupRemoved = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // Remove linhas completamente vazias ou só com separadores
            if (trimmed.isEmpty() || trimmed.matches("[,;|\\s]+")) {
                emptyRemoved++;
                continue;
            }

            // Remove duplicatas exatas
            if (!seen.add(trimmed)) {
                dupRemoved++;
                continue;
            }

            // Normaliza separadores múltiplos
            cleaned.add(trimmed.replaceAll(",{2,}", ",")
                               .replaceAll(";{2,}", ";"));
        }

        if (emptyRemoved > 0) {
            issues.add(new TextCleaningResult.CleaningIssue(
                "EMPTY_ROWS", "Linhas vazias removidas", emptyRemoved));
        }
        if (dupRemoved > 0) {
            issues.add(new TextCleaningResult.CleaningIssue(
                "DUPLICATE_ROWS", "Linhas duplicadas removidas", dupRemoved));
        }

        return String.join("\n", cleaned);
    }

    // ── Limpeza TXT Questões ─────────────────────────────────────────────
    private static final Pattern PATTERN_INLINE_GABARITO =
            Pattern.compile("(?m)^\\s*(Gabarito|GABARITO|Resposta|RESPOSTA)" +
                            "\\s*[:.]?\\s*(CERTO|ERRADO|C|E|V|F)\\s*$");

    private String cleanTxtQuestions(String text,
                                      List<TextCleaningResult.CleaningIssue> issues) {
        String result = text;

        // Remove gabaritos inline (não devem ir para o vetor)
        int count = countPattern(result, PATTERN_INLINE_GABARITO);
        result = PATTERN_INLINE_GABARITO.matcher(result).replaceAll("");
        if (count > 0) {
            issues.add(new TextCleaningResult.CleaningIssue(
                "INLINE_GABARITO",
                "Gabaritos inline removidos (não devem influenciar embeddings)",
                count
            ));
        }

        // Normaliza numeração de questões
        result = result.replaceAll("(?m)^\\s*(Q|QUEST[ÃA]O|Questão)\\s*(\\d+)",
                "Questão $2");

        return result;
    }

    // ── Limpeza TXT Plano ────────────────────────────────────────────────
    private String cleanTxtPlain(String text,
                                  List<TextCleaningResult.CleaningIssue> issues) {
        // Remove linhas em branco excessivas (> 2 consecutivas)
        String result = text.replaceAll("(\r?\n){3,}", "\n\n");

        // Remove espaços no início/fim de cada linha
        result = Arrays.stream(result.split("\n"))
                .map(String::trim)
                .collect(Collectors.joining("\n"));

        return result;
    }

    // ── Limpeza Genérica ─────────────────────────────────────────────────
    private String cleanGeneric(String text,
                                 List<TextCleaningResult.CleaningIssue> issues) {
        return normalizeWhitespace(text);
    }

    // ── Utilitários de limpeza ───────────────────────────────────────────

    private String normalizeUnicode(String text) {
        // NFD → decompõe acentos, NFC → recompõe
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    private String removeControlChars(String text) {
        // Remove caracteres de controle exceto \t, \n, \r
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    private String rejoinBrokenParagraphs(String text) {
        // Linhas quebradas no meio de frases (sem ponto no final)
        // são reunidas com o próximo parágrafo
        return text.replaceAll(
                "([a-záàãâéêíóõôúç,;])\\n([a-záàãâéêíóõôúçA-Z])",
                "$1 $2"
        );
    }

    private String removeDuplicateLines(String text) {
        String[] lines = text.split("\n");
        // Preserva ordem, remove duplicatas exatas (case-insensitive para linhas curtas)
        Set<String> seen = new LinkedHashSet<>();
        return Arrays.stream(lines)
                .filter(line -> {
                    String key = line.length() < 80
                            ? line.trim().toLowerCase() : line.trim();
                    return key.isBlank() || seen.add(key);
                })
                .collect(Collectors.joining("\n"));
    }

    private String removeStopWords(String text) {
        // Aplica apenas em palavras isoladas (não parte de termos compostos)
        return Arrays.stream(text.split("\\s+"))
                .filter(w -> !STOP_WORDS.contains(w.toLowerCase()
                        .replaceAll("[^a-záàãâéêíóõôúç]", "")))
                .collect(Collectors.joining(" "));
    }

    private String normalizeWhitespace(String text) {
        return text.replaceAll("[ \\t]+", " ")
                   .replaceAll("(\r?\n){3,}", "\n\n")
                   .trim();
    }

    // ── Cálculo de confiança ─────────────────────────────────────────────
    private double calculateConfidence(String original, String cleaned,
                                        CleaningStrategy strategy,
                                        List<TextCleaningResult.CleaningIssue> issues) {
        double confidence = strategy.baseConfidence;

        // Reduz confiança se removeu muito conteúdo
        double reductionPct = original.isEmpty() ? 0
                : (double)(original.length() - cleaned.length()) / original.length();

        if (reductionPct > 0.60) {
            confidence -= 0.30; // removeu mais de 60% — suspeito
        } else if (reductionPct > 0.40) {
            confidence -= 0.15;
        } else if (reductionPct > 0.20) {
            confidence -= 0.05;
        }

        // Aumenta confiança se texto limpo ainda tem conteúdo substancial
        if (cleaned.length() > 500) confidence += 0.05;

        // Reduz se encontrou muitos tipos diferentes de problemas
        long criticalIssues = issues.stream()
                .filter(i -> i.occurrences() > 10).count();
        if (criticalIssues > 3) confidence -= 0.10;

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private boolean hasHighRiskIssues(List<TextCleaningResult.CleaningIssue> issues) {
        return issues.stream().anyMatch(i ->
                i.type().equals("CONTROL_CHARS") && i.occurrences() > 100
        );
    }

    // ── Detecção de tipo de documento ───────────────────────────────────
    private static final Set<String> LEGAL_TERMS = Set.of(
        "art.", "artigo", "parágrafo", "inciso", "alínea",
        "lei", "decreto", "resolução", "portaria", "instrução normativa",
        "tribunal", "constituição", "regulamento", "estatuto",
        "§", "cf/88", "cf88", "lrf", "lei complementar"
    );

    private static final Set<String> ACADEMIC_TERMS = Set.of(
        "abstract", "resumo", "introdução", "metodologia",
        "referências", "conclusão", "apud", "ibidem", "et al",
        "doi:", "issn", "ibge", "dissertação", "tese"
    );

    private boolean containsLegalTerms(String sample) {
        String lower = sample.toLowerCase();
        long count = LEGAL_TERMS.stream()
                .filter(lower::contains).count();
        return count >= 3;
    }

    private boolean containsAcademicTerms(String sample) {
        String lower = sample.toLowerCase();
        long count = ACADEMIC_TERMS.stream()
                .filter(lower::contains).count();
        return count >= 2;
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private int countLines(String text) {
        return text.split("\n", -1).length;
    }

    private int countWords(String text) {
        if (text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private int countPattern(String text, Pattern pattern) {
        var m = pattern.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    private TextCleaningResult buildResult(String original, String cleaned,
                                            CleaningStrategy strategy,
                                            double confidence,
                                            boolean requiresManual,
                                            List<TextCleaningResult.CleaningIssue> issues,
                                            int stopWords, int specialChars,
                                            int dupLines) {
        int removedChars = original.length() - cleaned.length();
        double reductionPct = original.isEmpty() ? 0
                : (double) removedChars / original.length() * 100;

        return new TextCleaningResult(
                original, cleaned, strategy, confidence, requiresManual,
                issues,
                new TextCleaningResult.CleaningStats(
                        original.length(), cleaned.length(),
                        Math.max(0, removedChars),
                        dupLines, stopWords, specialChars,
                        Math.round(reductionPct * 100.0) / 100.0
                )
        );
    }
}