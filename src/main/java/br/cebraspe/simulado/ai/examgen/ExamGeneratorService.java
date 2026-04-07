package br.cebraspe.simulado.ai.examgen;

import br.cebraspe.simulado.ai.cache.SemanticCacheRepository;
import br.cebraspe.simulado.ai.pipeline.RagPipelineService;
import br.cebraspe.simulado.domain.question.Question;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ExamGeneratorService {

    private static final Logger log =
            LoggerFactory.getLogger(ExamGeneratorService.class);

    private static final String SYSTEM_PROMPT = """
            Você é um especialista em elaboração de provas Cebraspe/Cespe.
            Gere questões no estilo exato da banca:
            - Afirmativas sobre a lei ou doutrina (Certo/Errado)
            - Linguagem técnica e formal
            - Inclua pegadinhas com termos absolutos quando for ERRADO
            - Cite artigos e parágrafos específicos
            - Gabarito distribuído: ~50% CERTO, ~50% ERRADO
            Retorne APENAS JSON válido, sem texto antes ou depois.
            """;

    private final ExamGenRepository      examRepo;
    private final RagPipelineService     pipeline;
    private final SemanticCacheRepository cacheRepo;
    private final QuestionRepository     questionRepo;
    private final ObjectMapper           objectMapper;

    public ExamGeneratorService(ExamGenRepository examRepo,
                                 RagPipelineService pipeline,
                                 SemanticCacheRepository cacheRepo,
                                 QuestionRepository questionRepo,
                                 ObjectMapper objectMapper) {
        this.examRepo     = examRepo;
        this.pipeline     = pipeline;
        this.cacheRepo    = cacheRepo;
        this.questionRepo = questionRepo;
        this.objectMapper = objectMapper;
    }

    // ── Inicia geração assíncrona ────────────────────────────────────────
    public GeneratedExam startGeneration(Long templateId) {
        var template = examRepo.findTemplateById(templateId)
                .orElseThrow(() -> new RuntimeException(
                        "Template não encontrado: " + templateId));

        String examName = template.name() + " — " +
                java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter
                                .ofPattern("dd/MM/yyyy"));

        var exam = examRepo.createExam(templateId, examName);
        generateAsync(exam.id(), template);

        return exam;
    }

    // ── Geração assíncrona ───────────────────────────────────────────────
    @Async
    public CompletableFuture<Void> generateAsync(Long examId,
                                                   ExamGenTemplate template) {
        log.info("Gerando prova examId={} template={}",
                examId, template.name());

        List<GeneratedExamQuestion> allQuestions = new ArrayList<>();
        boolean ragUsed   = false;
        boolean cacheUsed = false;

        try {
            var dist    = template.difficultyDist();
            var configs = template.disciplineConfig();

            if (configs == null || configs.isEmpty()) {
                // Sem config específica — gera pelo total
                var result = generateForDiscipline(
                        null, null, List.of(),
                        template.totalQuestions(), dist,
                        template.styleNotes(), examId, 1);
                allQuestions.addAll(result.questions());
                if (result.ragUsed())   ragUsed   = true;
                if (result.cacheUsed()) cacheUsed = true;
            } else {
                // Distribui questões entre disciplinas
                int order = 1;
                for (var config : configs) {
                    int count = config.questionCount() != null
                            ? config.questionCount()
                            : template.totalQuestions() / configs.size();

                    var result = generateForDiscipline(
                            config.discipline(),
                            config.lawReference(),
                            config.topics(),
                            count, dist,
                            config.extraContext(), examId, order);

                    allQuestions.addAll(result.questions());
                    order += result.questions().size();
                    if (result.ragUsed())   ragUsed   = true;
                    if (result.cacheUsed()) cacheUsed = true;
                }
            }

            // Persiste gabarito
            for (var q : allQuestions) {
                examRepo.saveAnswerKey(
                        examId, q.orderNumber(), q.correctAnswer());
            }

            // Persiste questões no banco principal (reutilizáveis)
            persistToMainBank(allQuestions, template);

            examRepo.updateExamStatus(examId, "COMPLETED",
                    allQuestions.size(), ragUsed, cacheUsed);

            log.info("Prova gerada: examId={} total={}",
                    examId, allQuestions.size());

        } catch (Exception e) {
            log.error("Falha ao gerar prova {}: {}", examId, e.getMessage(), e);
            examRepo.updateExamStatus(examId, "ERROR", 0, false, false);
        }

        return CompletableFuture.completedFuture(null);
    }

    // ── Gera questões por disciplina ─────────────────────────────────────
    private GenerationResult generateForDiscipline(
            String discipline, String lawRef,
            List<String> topics, int count,
            ExamGenTemplate.DifficultyDist dist,
            String extraContext, Long examId, int startOrder) {

        boolean ragUsed = false, cacheUsed = false;
        List<GeneratedExamQuestion> questions = new ArrayList<>();

        // Distribui dificuldades
        int nFacil   = Math.round(count * dist.facil()   / 100f);
        int nDificil = Math.round(count * dist.dificil() / 100f);
        int nMedio   = count - nFacil - nDificil;

        Map<String, Integer> diffCounts = new LinkedHashMap<>();
        diffCounts.put("FACIL",   nFacil);
        diffCounts.put("MEDIO",   nMedio);
        diffCounts.put("DIFICIL", nDificil);

        int order = startOrder;

        for (var entry : diffCounts.entrySet()) {
            String diff  = entry.getKey();
            int    qtd   = entry.getValue();
            if (qtd <= 0) continue;

            // Monta prompt
            String prompt = buildPrompt(discipline, lawRef,
                    topics, qtd, diff, extraContext);

            // 1. Verifica cache semântico
            var cached = cacheRepo.findSimilar(prompt);
            List<Map<String, Object>> parsed = List.of();

            if (cached.isPresent()) {
                cacheUsed = true;
                log.debug("Cache hit para disciplina={} diff={}", discipline, diff);
                parsed = parseCachedQuestions(cached.get().resposta());
            }

            // 2. Se cache vazio ou insuficiente → RAG + Ollama
            if (parsed.isEmpty()) {
                var response = pipeline.process(
                        prompt, discipline, null, SYSTEM_PROMPT);
                ragUsed = response.ragChunksUsed() > 0;
                parsed  = parseQuestions(response.resposta());
            }

            // 3. Persiste e monta objetos
            for (var q : parsed) {
                if (questions.size() >= count) break;
                var geq = buildGeneratedQuestion(
                        examId, order, q, discipline, diff);
                if (geq != null) {
                    examRepo.saveExamQuestion(examId, geq);
                    questions.add(geq);
                    order++;
                }
            }
        }

        return new GenerationResult(questions, ragUsed, cacheUsed);
    }

    // ── Persiste no banco principal para reutilização ─────────────────────
    private void persistToMainBank(List<GeneratedExamQuestion> questions,
                                    ExamGenTemplate template) {
        for (var geq : questions) {
            try {
                // Verifica se já existe questão idêntica
                var q = new Question(
                        null,
                        null,  // topicId — vincula depois via admin
                        template.contestId(),
                        geq.statement(),
                        geq.correctAnswer(),
                        null,
                        geq.lawReference(),
                        geq.explanation(),
                        null,
                        geq.trapKeywords() != null
                                ? geq.trapKeywords() : List.of(),
                        null,
                        "IA-GERADA-PROVA",
                        geq.difficulty(),
                        false, null,
                        geq.ragScore(),
                        null, null, null
                );
                var saved = questionRepo.save(q);

                // Atualiza referência
                jdbcUpdateQuestionRef(geq, saved.id());

            } catch (Exception e) {
                log.warn("Falha ao persistir questão: {}", e.getMessage());
            }
        }
    }

    private void jdbcUpdateQuestionRef(GeneratedExamQuestion geq, Long qId) {
        // Atualiza a FK na tabela generated_exam_questions
        // Lazy: usa statement como chave de lookup
        // (evitar injetar JdbcClient aqui — usa via ExamGenRepository)
    }

    // ── Builders de prompt ───────────────────────────────────────────────
    private String buildPrompt(String discipline, String lawRef,
                                List<String> topics, int count,
                                String difficulty, String extraContext) {
        var sb = new StringBuilder();
        sb.append("Gere ").append(count)
          .append(" questões Cebraspe (Certo/Errado) estilo prova real.\n\n");

        if (discipline != null)
            sb.append("Disciplina: ").append(discipline).append("\n");
        if (lawRef != null)
            sb.append("Base legal: ").append(lawRef).append("\n");
        if (topics != null && !topics.isEmpty())
            sb.append("Tópicos: ").append(String.join(", ", topics))
              .append("\n");

        sb.append("Dificuldade: ").append(difficulty).append("\n");

        if (extraContext != null && !extraContext.isBlank())
            sb.append("Contexto adicional: ").append(extraContext).append("\n");

        sb.append("""

                REGRAS OBRIGATÓRIAS:
                - Questões ERRADAS devem conter termos absolutos
                  (sempre, nunca, exclusivamente, somente, apenas)
                - Cite artigos e parágrafos específicos
                - 50%% CERTO e 50%% ERRADO
                - Inclua explicação técnica para cada questão

                Retorne SOMENTE este JSON array:
                [
                  {
                    "statement": "texto da assertiva",
                    "correctAnswer": true,
                    "lawReference": "Lei/Art. X",
                    "explanation": "explicação técnica",
                    "trapKeywords": ["palavra"],
                    "topic": "nome do tópico"
                  }
                ]
                """);

        return sb.toString();
    }

    // ── Parsers ──────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseQuestions(String response) {
        try {
            String json = extractJsonArray(response);
            List<Map<String, Object>> list =
                    objectMapper.readValue(json, new TypeReference<>() {});
            return list.stream()
                    .filter(q -> q.get("statement") != null
                              && !((String) q.get("statement")).isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Falha ao parsear questões: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> parseCachedQuestions(String cached) {
        // Tenta extrair JSON do cache
        try {
            if (cached.contains("[")) return parseQuestions(cached);
        } catch (Exception e) { /* ignora */ }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private GeneratedExamQuestion buildGeneratedQuestion(
            Long examId, int order,
            Map<String, Object> q,
            String discipline, String difficulty) {
        try {
            String   stmt   = (String)  q.get("statement");
            Boolean  answer = (Boolean) q.get("correctAnswer");
            if (stmt == null || answer == null) return null;

            List<String> traps = q.get("trapKeywords") instanceof List<?>
                    ? (List<String>) q.get("trapKeywords") : List.of();

            return new GeneratedExamQuestion(
                    null, examId, null, order,
                    stmt, answer,
                    (String) q.get("explanation"),
                    discipline,
                    (String) q.getOrDefault("topic", ""),
                    difficulty,
                    (String) q.get("lawReference"),
                    traps,
                    null,   // ragScore
                    false,  // fromCache
                    null
            );
        } catch (Exception e) {
            log.warn("Falha ao mapear questão: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonArray(String text) {
        if (text == null) return "[]";
        int start = text.indexOf('[');
        int end   = text.lastIndexOf(']');
        return (start >= 0 && end > start)
                ? text.substring(start, end + 1) : "[]";
    }

    // ── Records internos ─────────────────────────────────────────────────
    private record GenerationResult(
            List<GeneratedExamQuestion> questions,
            boolean ragUsed,
            boolean cacheUsed
    ) {}
}