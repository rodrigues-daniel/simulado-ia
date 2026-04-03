package br.cebraspe.simulado.ai;


import br.cebraspe.simulado.ai.rag.RagSearchService;
import br.cebraspe.simulado.domain.question.Question;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import br.cebraspe.simulado.domain.topic.TopicRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGeneratorService.class);

    private final OllamaService        ollamaService;
    private final RagSearchService     ragSearchService;
    private final QuestionRepository   questionRepository;
    private final TopicRepository      topicRepository;
    private final ObjectMapper         objectMapper;

    public QuestionGeneratorService(OllamaService ollamaService,
                                    RagSearchService ragSearchService,
                                    QuestionRepository questionRepository,
                                    TopicRepository topicRepository,
                                    ObjectMapper objectMapper) {
        this.ollamaService      = ollamaService;
        this.ragSearchService   = ragSearchService;
        this.questionRepository = questionRepository;
        this.topicRepository    = topicRepository;
        this.objectMapper       = objectMapper;
    }

    public List<Question> generateQuestions(Long topicId, Integer count) {
        var topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Tópico não encontrado: " + topicId));

        // Busca contexto RAG
        var ragResults = ragSearchService.searchWithMetadata(topic.name(), 5);
        String ragContext = ragResults.stream()
                .map(RagSearchService.RagSearchResult::content)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Score médio de similaridade do RAG (confidence)
        double ragConfidence = ragResults.stream()
                .mapToDouble(r -> r.score() != null ? r.score() : 0.0)
                .average()
                .orElse(0.0);

        var prompt = buildGeneratorPrompt(topic.name(), topic.lawReference(),
                ragContext, count);

        String response = ragContext.isBlank()
                ? ollamaService.chat(prompt)
                : ollamaService.chatWithContext(prompt, ragContext);

        return parseQuestions(response, topicId, ragConfidence);
    }

    private String buildGeneratorPrompt(String topicName, String lawRef,
                                        String context, Integer count) {
        return """
                Gere %d questões no estilo CEBRASPE (Certo/Errado) sobre o tema:
                Tópico: %s | Base legal: %s

                REGRAS OBRIGATÓRIAS:
                - Use linguagem formal e técnica
                - Inclua palavras-chave armadilha (sempre, nunca, exclusivamente, somente) em algumas questões ERRADAS
                - Cite artigos/parágrafos específicos
                - Varie entre CERTO e ERRADO (aprox. 50/50)

                Retorne SOMENTE JSON array, sem texto antes ou depois:
                [
                  {
                    "statement": "texto da assertiva",
                    "correctAnswer": true,
                    "lawParagraph": "Art. X, §Y da Lei Z...",
                    "lawReference": "Lei nº X/XXXX",
                    "explanation": "explicação técnica",
                    "professorTip": "dica do professor sobre esta questão",
                    "trapKeywords": ["palavra1"],
                    "difficulty": "FACIL"
                  }
                ]
                """.formatted(count, topicName,
                lawRef != null ? lawRef : "legislação pertinente");
    }

    // ── CORREÇÃO: TypeReference explícito evita o problema de inferência ──
    private List<Question> parseQuestions(String response, Long topicId,
                                          double ragConfidence) {
        try {
            String json = extractJsonArray(response);

            // TypeReference explícito resolve o "Object cannot be converted to Question"
            List<Map<String, Object>> list = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {}
            );

            return list.stream()
                    .map(map -> mapToQuestion(map, topicId, ragConfidence))
                    .map(questionRepository::save)
                    .toList();

        } catch (Exception e) {
            log.warn("Erro ao parsear questões da IA: {}", e.getMessage());
            return List.of();
        }
    }

    private Question mapToQuestion(Map<String, Object> map, Long topicId,
                                   double ragConfidence) {
        // trapKeywords pode vir como List<String> ou null
        @SuppressWarnings("unchecked")
        List<String> traps = map.get("trapKeywords") instanceof List<?>
                ? (List<String>) map.get("trapKeywords")
                : List.of();

        return new Question(
                null,
                topicId,
                null,
                (String)  map.get("statement"),
                (Boolean) map.get("correctAnswer"),
                (String)  map.get("lawParagraph"),
                (String)  map.get("lawReference"),
                (String)  map.get("explanation"),
                (String)  map.get("professorTip"),
                traps,
                null,
                "IA-GERADA",
                (String)  map.getOrDefault("difficulty", "MEDIO"),
                // ── Campos IA ──────────────────────────────────────────
                false,                                    // iaReviewed — pendente
                null,                                     // iaApproved — aguardando
                BigDecimal.valueOf(ragConfidence),        // iaConfidence
                null,                                     // reviewNote
                null,                                     // reviewedAt
                // ───────────────────────────────────────────────────────
                null                                      // createdAt
        );
    }

    private String extractJsonArray(String text) {
        if (text == null) return "[]";
        int start = text.indexOf('[');
        int end   = text.lastIndexOf(']');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return "[]";
    }
}