package br.cebraspe.simulado.ai;

import br.cebraspe.simulado.ai.rag.RagSearchService;
import br.cebraspe.simulado.domain.question.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProfessorExplanationService {

    private static final Logger log =
            LoggerFactory.getLogger(ProfessorExplanationService.class);

    private final OllamaService               ollamaService;
    private final RagSearchService            ragSearchService;
    private final IAExplanationCacheRepository cacheRepository;

    public ProfessorExplanationService(OllamaService ollamaService,
                                       RagSearchService ragSearchService,
                                       IAExplanationCacheRepository cacheRepository) {
        this.ollamaService   = ollamaService;
        this.ragSearchService = ragSearchService;
        this.cacheRepository  = cacheRepository;
    }

    /**
     * Gera explicação com cache:
     * 1. Verifica cache no banco
     * 2. Se não tiver, gera via RAG + Ollama e salva no cache
     */
    public ExplanationResult generateExplanation(Question question,
                                                  Boolean userAnswer) {
        // ── 1. Verifica cache ───────────────────────────────────────────
        var cached = cacheRepository.find(question.id(), userAnswer);
        if (cached.isPresent()) {
            log.debug("Cache hit para questionId={} userAnswer={}",
                    question.id(), userAnswer);
            return new ExplanationResult(
                    cached.get().explanation(),
                    cached.get().ragSource() ? Source.CACHE_RAG : Source.CACHE_DB,
                    cached.get().ragAvgScore() != null
                            ? cached.get().ragAvgScore().doubleValue() : 0.0
            );
        }

        // ── 2. Busca contexto RAG ───────────────────────────────────────
        List<RagSearchService.RagSearchResult> ragResults = List.of();
        String ragContext = "";
        double ragAvgScore = 0.0;
        boolean usedRag = false;

        try {
            ragResults = ragSearchService.searchWithMetadata(
                    question.statement(), 3);
            ragContext = ragResults.stream()
                    .map(RagSearchService.RagSearchResult::content)
                    .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));
            ragAvgScore = ragResults.stream()
                    .mapToDouble(r -> r.score() != null ? r.score() : 0.0)
                    .average().orElse(0.0);
            usedRag = !ragContext.isBlank();
        } catch (Exception e) {
            log.warn("RAG indisponível: {}", e.getMessage());
        }

        // ── 3. Gera via Ollama ──────────────────────────────────────────
        String prompt = buildProfessorPrompt(question, userAnswer);
        String explanation;
        Source source;

        try {
            explanation = usedRag
                    ? ollamaService.chatWithContext(prompt, ragContext)
                    : ollamaService.chat(prompt);
            source = usedRag ? Source.AI_RAG : Source.AI_ONLY;
        } catch (Exception e) {
            log.warn("Ollama indisponível questionId={}: {}", question.id(), e.getMessage());
            explanation = question.explanation() != null
                    ? question.explanation()
                    : "Revise o parágrafo da lei indicado acima.";
            source = Source.FALLBACK;
        }

        // ── 4. Salva no cache ───────────────────────────────────────────
        try {
            cacheRepository.save(question.id(), userAnswer, explanation,
                    usedRag, ragAvgScore > 0 ? ragAvgScore : null);
        } catch (Exception e) {
            log.warn("Falha ao salvar cache questionId={}: {}", question.id(), e.getMessage());
        }

        return new ExplanationResult(explanation, source, ragAvgScore);
    }

    private String buildProfessorPrompt(Question question, Boolean userAnswer) {
        return """
                Um aluno errou a seguinte questão Cebraspe. Explique como um professor
                experiente, identificando a PEGADINHA, o FUNDAMENTO LEGAL e alertando
                sobre palavras-chave perigosas.

                QUESTÃO: %s

                GABARITO: %s
                RESPOSTA DO ALUNO: %s

                BASE LEGAL: %s
                PARÁGRAFO ESPECÍFICO: %s

                Palavras-chave presentes: %s

                Formate sua explicação assim:
                🎯 PEGADINHA: [identifique a armadilha da questão]
                📖 FUNDAMENTO: [cite o artigo/parágrafo específico da lei]
                ⚠️ ATENÇÃO: [destaque a palavra-chave perigosa e por que ela induz ao erro]
                💡 DICA DO PROFESSOR: [dê uma dica memorável para não errar mais]
                """.formatted(
                question.statement(),
                question.correctAnswer() ? "CERTO" : "ERRADO",
                userAnswer != null && userAnswer ? "CERTO" : "ERRADO",
                question.lawReference()  != null ? question.lawReference()  : "ver material",
                question.lawParagraph()  != null ? question.lawParagraph()  : "ver parágrafo vinculado",
                question.trapKeywords()  != null
                        ? String.join(", ", question.trapKeywords()) : "nenhuma"
        );
    }

    public enum Source {
        CACHE_DB,   // cache do banco, gerado antes sem RAG
        CACHE_RAG,  // cache do banco, gerado com RAG
        AI_RAG,     // gerado agora com Ollama + RAG
        AI_ONLY,    // gerado agora com Ollama sem RAG
        FALLBACK    // Ollama indisponível, explicação estática
    }

    public record ExplanationResult(
            String explanation,
            Source source,
            double ragScore
    ) {}
}