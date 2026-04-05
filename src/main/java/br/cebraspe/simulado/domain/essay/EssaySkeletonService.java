package br.cebraspe.simulado.domain.essay;

import br.cebraspe.simulado.ai.pipeline.RagPipelineService;
import br.cebraspe.simulado.domain.topic.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class EssaySkeletonService {

    private final EssaySkeletonRepository essaySkeletonRepository;
    private final RagPipelineService      pipeline;   // ← substitui OllamaService
    private final TopicRepository         topicRepository;
    private final ObjectMapper            objectMapper;

    private static final String ESSAY_SYSTEM =
            """
            Você é especialista em redação discursiva para concursos Cebraspe.
            Gere esqueletos estruturados, técnicos e objetivos.
            Sempre inclua palavras-chave obrigatórias e dicas específicas da banca.
            Retorne APENAS JSON válido, sem texto antes ou depois.
            """;

    public EssaySkeletonService(EssaySkeletonRepository essaySkeletonRepository,
                                 RagPipelineService pipeline,
                                 TopicRepository topicRepository,
                                 ObjectMapper objectMapper) {
        this.essaySkeletonRepository = essaySkeletonRepository;
        this.pipeline                = pipeline;
        this.topicRepository         = topicRepository;
        this.objectMapper            = objectMapper;
    }

    public EssaySkeleton generateSkeleton(Long topicId, Long contestId) {
        var topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException(
                        "Tópico não encontrado"));

        String pergunta = buildEssayPrompt(
                topic.name(), topic.discipline(), topic.lawReference());

        // Pipeline: cache → RAG → Ollama → salva cache
        var response = pipeline.process(
                pergunta,
                topic.discipline(),
                topicId,
                ESSAY_SYSTEM
        );

        var skeleton = parseEssaySkeleton(
                response.resposta(), topicId, contestId);

        return essaySkeletonRepository.save(skeleton);
    }

    private String buildEssayPrompt(String topicName,
                                     String discipline,
                                     String lawReference) {
        return """
                Gere um ESQUELETO DE REDAÇÃO DISCURSIVA para o tópico:
                Tópico: %s | Disciplina: %s | Base legal: %s
                
                Retorne EXATAMENTE neste JSON:
                {
                  "title": "título da redação",
                  "introduction": "parágrafo de introdução",
                  "bodyPoints": ["ponto 1", "ponto 2", "ponto 3"],
                  "conclusion": "parágrafo de conclusão",
                  "mandatoryKeywords": ["palavra1", "palavra2"],
                  "bancaTips": "dica específica Cebraspe"
                }
                """.formatted(
                topicName,
                discipline    != null ? discipline    : "Direito Público",
                lawReference  != null ? lawReference  : "legislação pertinente"
        );
    }

    @SuppressWarnings("unchecked")
    private EssaySkeleton parseEssaySkeleton(String aiResponse,
                                              Long topicId,
                                              Long contestId) {
        try {
            String json = extractJson(aiResponse);
            var map     = objectMapper.readValue(json, Map.class);
            return new EssaySkeleton(
                    null, topicId, contestId,
                    (String)       map.get("title"),
                    (String)       map.get("introduction"),
                    (List<String>) map.get("bodyPoints"),
                    (String)       map.get("conclusion"),
                    (List<String>) map.get("mandatoryKeywords"),
                    (String)       map.get("bancaTips"),
                    30, true, null
            );
        } catch (Exception e) {
            return new EssaySkeleton(null, topicId, contestId,
                    "Esqueleto gerado automaticamente",
                    "Introdução: contextualize o tema.",
                    List.of("Conceito e fundamento legal",
                            "Aplicação prática",
                            "Controle e exceções"),
                    "Conclusão: reforce o princípio fundamental.",
                    List.of("legalidade", "eficiência", "publicidade"),
                    "Atenção aos prazos e competências.", 30, true, null);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }
}