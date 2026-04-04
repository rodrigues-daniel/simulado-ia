package br.cebraspe.simulado.ai;

import br.cebraspe.simulado.ai.pipeline.RagPipelineService;
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

    private static final Logger log =
            LoggerFactory.getLogger(QuestionGeneratorService.class);

    private final RagPipelineService pipeline;
    private final QuestionRepository questionRepository;
    private final TopicRepository    topicRepository;
    private final ObjectMapper       objectMapper;

    private static final String GENERATOR_SYSTEM =
            """
            Você é especialista em concursos Cebraspe.
            Gere questões técnicas no estilo exato da banca.
            Retorne APENAS JSON válido, sem texto antes ou depois.
            Use linguagem formal. Cite artigos e parágrafos específicos.
            Inclua pegadinhas com palavras absolutas (sempre, nunca,
            exclusivamente, somente) nas questões ERRADAS.
            """;

    public QuestionGeneratorService(RagPipelineService pipeline,
                                     QuestionRepository questionRepository,
                                     TopicRepository topicRepository,
                                     ObjectMapper objectMapper) {
        this.pipeline           = pipeline;
        this.questionRepository = questionRepository;
        this.topicRepository    = topicRepository;
        this.objectMapper       = objectMapper;
    }

    public List<Question> generateQuestions(Long topicId, Integer count) {
        var topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException(
                        "Tópico não encontrado: " + topicId));

        String pergunta = buildGeneratorPrompt(
                topic.name(), topic.lawReference(), topic.discipline(), count);

        // Pipeline completo: cache → RAG → Ollama
        var response = pipeline.process(
                pergunta,
                topic.discipline(),
                topicId,
                GENERATOR_SYSTEM
        );

        double ragConfidence = response.ragChunksUsed() > 0 ? 0.8 : 0.4;

        log.info("Gerador: fonte={} chunks={} topico={}",
                response.source(), response.ragChunksUsed(), topic.name());

        return parseAndSave(response.resposta(), topicId, ragConfidence);
    }

    private String buildGeneratorPrompt(String topicName, String lawRef,
                                         String discipline, int count) {
        return """
                Gere %d questões no estilo CEBRASPE (Certo/Errado) sobre:
                Tópico: %s | Disciplina: %s | Base legal: %s
                
                REGRAS:
                - 50%% CERTO, 50%% ERRADO
                - Pegadinhas com: sempre, nunca, exclusivamente, somente, apenas
                - Cite artigos e parágrafos específicos
                - Varie dificuldade: FACIL/MEDIO/DIFICIL
                
                Retorne SOMENTE este JSON array:
                [{"statement":"...","correctAnswer":true,"lawParagraph":"Art...","lawReference":"Lei...","explanation":"...","professorTip":"...","trapKeywords":["palavra"],"difficulty":"FACIL"}]
                """.formatted(count, topicName,
                discipline != null ? discipline : "Direito Público",
                lawRef     != null ? lawRef     : "legislação pertinente");
    }

    private List<Question> parseAndSave(String response, Long topicId,
                                         double ragConfidence) {
        try {
            String json = extractJsonArray(response);
            List<Map<String, Object>> list = objectMapper.readValue(
                    json, new TypeReference<>() {});

            return list.stream()
                    .map(map -> mapToQuestion(map, topicId, ragConfidence))
                    .map(questionRepository::save)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Erro ao parsear questões: {}", e.getMessage());
            return List.of();
        }
    }

    private Question mapToQuestion(Map<String, Object> map, Long topicId,
                                    double ragConfidence) {
        @SuppressWarnings("unchecked")
        List<String> traps = map.get("trapKeywords") instanceof List<?>
                ? (List<String>) map.get("trapKeywords") : List.of();

        return new Question(
                null, topicId, null,
                (String)  map.get("statement"),
                (Boolean) map.getOrDefault("correctAnswer", false),
                (String)  map.get("lawParagraph"),
                (String)  map.get("lawReference"),
                (String)  map.get("explanation"),
                (String)  map.get("professorTip"),
                traps, null, "IA-GERADA",
                (String) map.getOrDefault("difficulty", "MEDIO"),
                false, null,
                BigDecimal.valueOf(ragConfidence),
                null, null, null
        );
    }

    private String extractJsonArray(String text) {
        if (text == null) return "[]";
        int start = text.indexOf('[');
        int end   = text.lastIndexOf(']');
        return (start >= 0 && end > start)
                ? text.substring(start, end + 1) : "[]";
    }
}