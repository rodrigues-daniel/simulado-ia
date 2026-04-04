package br.cebraspe.simulado.ai;

import br.cebraspe.simulado.ai.pipeline.RagPipelineService;
import br.cebraspe.simulado.domain.question.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class ProfessorExplanationService {

    private static final Logger log =
            LoggerFactory.getLogger(ProfessorExplanationService.class);


    private final RagPipelineService pipeline;
    private final IAExplanationCacheRepository legacyCache;

    private static final String PROFESSOR_SYSTEM =
            """
            Você é um professor especialista em concursos públicos Cebraspe.
            Explique de forma técnica, precisa e didática.
            Sempre cite artigos e parágrafos da lei quando disponível.
            Identifique pegadinhas e palavras-chave armadilha.
            """;

    public ProfessorExplanationService(
            RagPipelineService pipeline,
            IAExplanationCacheRepository legacyCache) {
        this.pipeline    = pipeline;
        this.legacyCache = legacyCache;
    }

    public ExplanationResult generateExplanation(Question question,
                                                 Boolean userAnswer) {
        // 1. Verifica cache legado (ia_explanation_cache)
        var cached = legacyCache.find(question.id(), userAnswer);
        if (cached.isPresent()) {
            return new ExplanationResult(
                    cached.get().explanation(),
                    Source.CACHE_DB,
                    0.0
            );
        }

        // 2. Monta pergunta contextualizada
        String pergunta = buildPergunta(question, userAnswer);

        // 3. Passa pelo pipeline completo (cache semântico → RAG → Ollama)
        var response = pipeline.process(
                pergunta,
                resolveMateriaFromQuestion(question),
                question.topicId(),
                PROFESSOR_SYSTEM
        );

        String explanation = response.resposta();
        Source source = switch (response.source()) {
            case CACHE_SEMANTICO -> Source.CACHE_RAG;
            case RAG_OLLAMA      -> Source.AI_RAG;
            case OLLAMA_ONLY     -> Source.AI_ONLY;
            default              -> Source.FALLBACK;
        };

        // 4. Salva no cache legado para acesso direto por questionId
        try {
            legacyCache.save(question.id(), userAnswer, explanation,
                    response.ragChunksUsed() > 0,
                    response.ragChunksUsed() > 0 ? 0.8 : null);
        } catch (Exception e) {
            log.warn("Cache legado falhou: {}", e.getMessage());
        }

        return new ExplanationResult(explanation, source, 0.0);
    }

    private String buildPergunta(Question question, Boolean userAnswer) {
        return """
                Um aluno errou a seguinte questão Cebraspe. Explique como professor,
                identificando a PEGADINHA, o FUNDAMENTO LEGAL e alertando sobre
                palavras-chave perigosas.
                
                QUESTÃO: %s
                
                GABARITO: %s
                RESPOSTA DO ALUNO: %s
                BASE LEGAL: %s
                PARÁGRAFO: %s
                PALAVRAS-CHAVE: %s
                
                Formate:
                🎯 PEGADINHA: [identifique]
                📖 FUNDAMENTO: [cite artigo/parágrafo]
                ⚠️ ATENÇÃO: [palavra-chave perigosa]
                💡 DICA: [dica memorável]
                """.formatted(
                question.statement(),
                question.correctAnswer() ? "CERTO" : "ERRADO",
                Boolean.TRUE.equals(userAnswer) ? "CERTO" : "ERRADO",
                question.lawReference()  != null ? question.lawReference()  : "ver material",
                question.lawParagraph()  != null ? question.lawParagraph()  : "ver parágrafo",
                question.trapKeywords()  != null
                        ? String.join(", ", question.trapKeywords()) : "nenhuma"
        );
    }

    private String resolveMateriaFromQuestion(Question question) {
        // Tenta inferir matéria pelo lawReference
        if (question.lawReference() == null) return null;
        String ref = question.lawReference().toLowerCase();
        if (ref.contains("cf") || ref.contains("constituição"))
            return "Direito Constitucional";
        if (ref.contains("lei 8.112") || ref.contains("regime jurídico"))
            return "Direito Administrativo";
        if (ref.contains("lei 9.784") || ref.contains("processo administrativo"))
            return "Direito Administrativo";
        return null;
    }

    public enum Source {
        CACHE_DB, CACHE_RAG, AI_RAG, AI_ONLY, FALLBACK
    }

    public record ExplanationResult(
            String explanation,
            Source source,
            double ragScore
    ) {}


}