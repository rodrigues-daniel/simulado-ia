package br.cebraspe.simulado.ai;

import br.cebraspe.simulado.ai.rag.RagSearchService;
import br.cebraspe.simulado.domain.question.Question;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import br.cebraspe.simulado.domain.topic.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class QuestionGeneratorService {

    private final OllamaService ollamaService;
    private final RagSearchService ragSearchService;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final ObjectMapper objectMapper;

    public QuestionGeneratorService(OllamaService ollamaService,
            RagSearchService ragSearchService,
            QuestionRepository questionRepository,
            TopicRepository topicRepository,
            ObjectMapper objectMapper) {
        this.ollamaService = ollamaService;
        this.ragSearchService = ragSearchService;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.objectMapper = objectMapper;
    }

    public List<Question> generateQuestions(Long topicId, Integer count) {
        var topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Tópico não encontrado"));

        var ragContext = ragSearchService.search(topic.name(), 5);
        var prompt = buildGeneratorPrompt(topic.name(), topic.lawReference(),
                ragContext, count);
        var response = ragContext.isBlank()
                ? ollamaService.chat(prompt)
                : ollamaService.chatWithContext(prompt, ragContext);

        return parseQuestions(response, topicId);
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

                Retorne SOMENTE JSON array:
                [
                  {
                    "statement": "texto da assertiva",
                    "correctAnswer": true,
                    "lawParagraph": "Art. X, §Y da Lei Z...",
                    "lawReference": "Lei nº X/XXXX",
                    "explanation": "explicação técnica",
                    "professorTip": "dica do professor sobre esta questão",
                    "trapKeywords": ["palavra1", "palavra2"],
                    "difficulty": "FACIL|MEDIO|DIFICIL"
                  }
                ]
                """.formatted(count, topicName, lawRef != null ? lawRef : "legislação pertinente");
    }

    @SuppressWarnings("unchecked")
    private List<Question> parseQuestions(String response, Long topicId) {
        try {
            String json = extractJsonArray(response);
            var list = objectMapper.readValue(json, List.class);
            return list.stream().map(item -> {
                var map = (Map<String, Object>) item;
                return new Question(null, topicId, null,
                        (String) map.get("statement"),
                        (Boolean) map.get("correctAnswer"),
                        (String) map.get("lawParagraph"),
                        (String) map.get("lawReference"),
                        (String) map.get("explanation"),
                        (String) map.get("professorTip"),
                        (List<String>) map.getOrDefault("trapKeywords", List.of()),
                        null, "IA-GERADA",
                        (String) map.getOrDefault("difficulty", "MEDIO"),
                        null);
            }).map(q -> questionRepository.save((Question) q)).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start)
            return text.substring(start, end + 1);
        return "[]";
    }
}