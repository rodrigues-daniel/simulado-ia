package br.cebraspe.simulado.ai;

import br.cebraspe.simulado.ai.rag.RagSearchService;
import br.cebraspe.simulado.domain.question.Question;
import org.springframework.stereotype.Service;

@Service
public class ProfessorExplanationService {

    private final OllamaService ollamaService;
    private final RagSearchService ragSearchService;

    public ProfessorExplanationService(OllamaService ollamaService,
            RagSearchService ragSearchService) {
        this.ollamaService = ollamaService;
        this.ragSearchService = ragSearchService;
    }

    /**
     * Gera explicação no estilo "professor Cebraspe":
     * - Aponta a pegadinha
     * - Cita o parágrafo exato da lei
     * - Alerta sobre palavras-chave perigosas
     */
    public String generateExplanation(Question question, Boolean userAnswer) {
        // Busca contexto do RAG para enriquecer a explicação
        var ragContext = ragSearchService.search(question.statement(), 3);

        var prompt = buildProfessorPrompt(question, userAnswer, ragContext);

        if (!ragContext.isBlank()) {
            return ollamaService.chatWithContext(prompt, ragContext);
        }
        return ollamaService.chat(prompt);
    }

    private String buildProfessorPrompt(Question question, Boolean userAnswer,
            String ragContext) {
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
                userAnswer ? "CERTO" : "ERRADO",
                question.lawReference() != null ? question.lawReference() : "ver material",
                question.lawParagraph() != null ? question.lawParagraph() : "ver parágrafo vinculado",
                question.trapKeywords() != null ? String.join(", ", question.trapKeywords()) : "nenhuma");
    }
}