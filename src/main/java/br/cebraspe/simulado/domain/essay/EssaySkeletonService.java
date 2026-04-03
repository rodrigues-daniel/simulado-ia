package br.cebraspe.simulado.domain.essay;

import br.cebraspe.simulado.ai.OllamaService;
import br.cebraspe.simulado.domain.topic.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class EssaySkeletonService {

    private final EssaySkeletonRepository essaySkeletonRepository;
    private final OllamaService ollamaService;
    private final TopicRepository topicRepository;
    private final ObjectMapper objectMapper;

    public EssaySkeletonService(EssaySkeletonRepository essaySkeletonRepository,
            OllamaService ollamaService,
            TopicRepository topicRepository,
            ObjectMapper objectMapper) {
        this.essaySkeletonRepository = essaySkeletonRepository;
        this.ollamaService = ollamaService;
        this.topicRepository = topicRepository;
        this.objectMapper = objectMapper;
    }

    public EssaySkeleton generateSkeleton(Long topicId, Long contestId) {
        var topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Tópico não encontrado"));

        var prompt = buildEssayPrompt(topic.name(), topic.discipline(),
                topic.lawReference());

        var aiResponse = ollamaService.chat(prompt);
        var skeleton = parseEssaySkeleton(aiResponse, topicId, contestId);

        return essaySkeletonRepository.save(skeleton);
    }

    private String buildEssayPrompt(String topicName, String discipline,
            String lawReference) {
        return """
                Você é um especialista em concursos públicos Cebraspe.
                Gere um ESQUELETO DE REDAÇÃO DISCURSIVA para o tópico abaixo.

                Tópico: %s
                Disciplina: %s
                Base legal: %s

                Retorne EXATAMENTE no formato JSON:
                {
                  "title": "título da redação",
                  "introduction": "parágrafo de introdução com contextualização",
                  "bodyPoints": [
                    "ponto 1: conceito principal com fundamento legal",
                    "ponto 2: aplicação prática",
                    "ponto 3: aspectos críticos ou exceções"
                  ],
                  "conclusion": "parágrafo de conclusão objetivo",
                  "mandatoryKeywords": ["palavra1", "palavra2", "palavra3"],
                  "bancaTips": "dica específica sobre o que o Cebraspe mais cobra neste tema"
                }

                Seja objetivo, técnico e use linguagem formal. Máximo 30 linhas.
                """.formatted(topicName, discipline,
                lawReference != null ? lawReference : "legislação pertinente");
    }

    @SuppressWarnings("unchecked")
    private EssaySkeleton parseEssaySkeleton(String aiResponse, Long topicId,
            Long contestId) {
        try {
            var json = extractJson(aiResponse);
            var map = objectMapper.readValue(json, Map.class);
            return new EssaySkeleton(
                    null,
                    topicId,
                    contestId,
                    (String) map.get("title"),
                    (String) map.get("introduction"),
                    (List<String>) map.get("bodyPoints"),
                    (String) map.get("conclusion"),
                    (List<String>) map.get("mandatoryKeywords"),
                    (String) map.get("bancaTips"),
                    30,
                    true,
                    null);
        } catch (Exception e) {
            // Fallback estruturado caso o JSON venha malformado
            return new EssaySkeleton(null, topicId, contestId,
                    "Esqueleto gerado automaticamente",
                    "Introdução: contextualize o tema com base na legislação.",
                    List.of("Ponto 1: Conceito e fundamento legal",
                            "Ponto 2: Aplicação e competências",
                            "Ponto 3: Controle e exceções"),
                    "Conclusão: reforce o princípio fundamental.",
                    List.of("legalidade", "eficiência", "publicidade"),
                    "Atenção aos prazos e competências do órgão.",
                    30, true, null);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start)
            return text.substring(start, end + 1);
        return text;
    }
}