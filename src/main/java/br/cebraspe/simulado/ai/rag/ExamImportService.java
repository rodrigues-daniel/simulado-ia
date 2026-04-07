package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.ai.pipeline.RagPipelineService;
import br.cebraspe.simulado.domain.question.Question;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamImportService {

    private static final Logger log = LoggerFactory.getLogger(ExamImportService.class);

    private static final int MAX_CHARS_PER_CHUNK = 6_000;
    private static final int PAGES_PER_BATCH = 10;

    private final JdbcClient jdbcClient;
    private final RagPipelineService pipeline;
    private final KnowledgeRepository knowledgeRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    public ExamImportService(JdbcClient jdbcClient,
            RagPipelineService pipeline,
            KnowledgeRepository knowledgeRepository,
            QuestionRepository questionRepository,
            ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.pipeline = pipeline;
        this.knowledgeRepository = knowledgeRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    // ── Recebe o upload e registra ────────────────────────────────────────
    public Map<String, Object> importExam(MultipartFile file,
            String name,
            Long contestId,
            Integer year,
            String role) throws IOException {
        long sizeMb = file.getSize() / 1024 / 1024;
        if (sizeMb > 100) {
            throw new IllegalArgumentException(
                    "Arquivo muito grande (" + sizeMb + " MB). Máximo: 100 MB.");
        }

        byte[] pdfBytes = file.getBytes();

        Long templateId = jdbcClient.sql("""
                INSERT INTO exam_templates
                    (name, contest_id, year, role, status, source_file)
                VALUES (:name, :contestId, :year, :role, 'PROCESSING', :sourceFile)
                RETURNING id
                """)
                .param("name", name)
                .param("contestId", contestId)
                .param("year", year)
                .param("role", role)
                .param("sourceFile", file.getOriginalFilename())
                .query(Long.class)
                .single();

        processExamAsync(templateId, pdfBytes, name, contestId);

        return Map.of(
                "templateId", templateId,
                "name", name,
                "status", "PROCESSING",
                "message", "Prova recebida. Processando em background...");
    }

    // ── Processamento assíncrono ──────────────────────────────────────────
    @Async
    public void processExamAsync(Long templateId, byte[] pdfBytes,
            String name, Long contestId) {
        log.info("Iniciando processamento: template={} name={}", templateId, name);

        List<Map<String, Object>> allQuestions = new ArrayList<>();

        // ── CORREÇÃO 1: Loader.loadPDF(byte[]) — API correta PDFBox 3.x ──
        // PDDocument agora implementa Closeable via Loader
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            int totalPages = document.getNumberOfPages();
            log.info("PDF '{}': {} páginas", name, totalPages);

            // Processa em lotes para controlar memória
            for (int startPage = 1; startPage <= totalPages; startPage += PAGES_PER_BATCH) {

                int endPage = Math.min(
                        startPage + PAGES_PER_BATCH - 1, totalPages);

                String pageText = extractPageRange(
                        document, startPage, endPage);

                log.debug("Lote páginas {}-{}: {} chars",
                        startPage, endPage, pageText.length());

                if (pageText.isBlank())
                    continue;

                List<String> chunks = splitIntoChunks(
                        pageText, MAX_CHARS_PER_CHUNK);

                for (String chunk : chunks) {
                    try {
                        var extracted = extractQuestionsFromChunk(chunk);
                        allQuestions.addAll(extracted);
                        log.debug("Chunk: {} questões extraídas",
                                extracted.size());
                    } catch (Exception e) {
                        log.warn("Falha no chunk: {}", e.getMessage());
                    }
                }
            }

        } catch (OutOfMemoryError e) {
            log.error("OOM ao processar prova {}: {}", templateId, e.getMessage());
            updateStatus(templateId, "ERROR",
                    "Arquivo muito grande para processar.");
            return;
        } catch (Exception e) {
            log.error("Erro ao processar prova {}: {}",
                    templateId, e.getMessage(), e);
            updateStatus(templateId, "ERROR", e.getMessage());
            return;
        } finally {
            System.gc();
        }

        // Remove duplicatas e persiste
        List<Map<String, Object>> unique = deduplicateQuestions(allQuestions);
        int saved = persistTemplateQuestions(templateId, unique);

        // Indexa resumo no RAG
        indexExamSummary(pdfBytes, name, contestId);

        jdbcClient.sql("""
                UPDATE exam_templates
                SET status = 'COMPLETED', total_questions = :total
                WHERE id   = :id
                """)
                .param("total", saved)
                .param("id", templateId)
                .update();

        log.info("Prova '{}' concluída: {} questões (templateId={})",
                name, saved, templateId);
    }

    // ── CORREÇÃO 2: tipo explícito PDDocument, não var ────────────────────
    private String extractPageRange(PDDocument doc,
            int startPage,
            int endPage) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(startPage);
        stripper.setEndPage(endPage);
        stripper.setSortByPosition(true);
        return stripper.getText(doc);
    }

    // ── Indexa resumo no RAG (apenas primeiras páginas) ───────────────────
    private void indexExamSummary(byte[] pdfBytes,
            String name, Long contestId) {
        // ── CORREÇÃO 3: mesma correção no segundo uso de Loader ────────────
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {

            int endPage = Math.min(5, doc.getNumberOfPages());
            String summary = extractPageRange(doc, 1, endPage);

            if (!summary.isBlank()) {
                String truncated = summary.length() > 3000
                        ? summary.substring(0, 3000)
                        : summary;
                knowledgeRepository.save(
                        truncated, "Prova Cebraspe",
                        null, contestId,
                        "Prova: " + name);
            }
        } catch (Exception e) {
            log.warn("Falha ao indexar resumo: {}", e.getMessage());
        }
    }

    // ── Divide texto em chunks ────────────────────────────────────────────
    private List<String> splitIntoChunks(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= maxChars) {
            chunks.add(text);
            return chunks;
        }

        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() > maxChars) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                if (para.length() > maxChars) {
                    for (String line : para.split("\n")) {
                        if (current.length() + line.length() > maxChars) {
                            if (!current.isEmpty()) {
                                chunks.add(current.toString().trim());
                                current.setLength(0);
                            }
                        }
                        current.append(line).append("\n");
                    }
                } else {
                    current.append(para).append("\n\n");
                }
            } else {
                current.append(para).append("\n\n");
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    // ── Extrai questões de um chunk via pipeline IA ───────────────────────
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractQuestionsFromChunk(String chunk) {
        String prompt = """
                Analise o trecho abaixo de uma prova Cebraspe (Certo/Errado).
                Extraia APENAS as questões completas encontradas.
                Ignore cabeçalhos, rodapés e textos que não são questões.

                Retorne SOMENTE um JSON array. Se não houver questões, retorne [].

                Formato obrigatório:
                [{"number":1,"statement":"texto completo","answer":true,
                  "discipline":"Direito Administrativo","topic":"Princípios"}]

                TRECHO:
                %s
                """.formatted(chunk);

        try {
            var response = pipeline.process(
                    prompt, null, null,
                    "Especialista em análise de provas Cebraspe. " +
                            "Responda apenas JSON.");

            String json = extractJsonArray(response.resposta());
            List<Map<String, Object>> list = objectMapper.readValue(
                    json, new TypeReference<>() {
                    });

            return list.stream()
                    .filter(q -> q.get("statement") != null
                            && !((String) q.get("statement")).isBlank())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Falha ao extrair questões: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Remove duplicatas ─────────────────────────────────────────────────
    private List<Map<String, Object>> deduplicateQuestions(
            List<Map<String, Object>> questions) {

        Set<String> seen = new LinkedHashSet<>();
        Set<Object> seenNums = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (var q : questions) {
            Object num = q.get("number");
            String stmt = ((String) q.getOrDefault("statement", ""))
                    .trim();
            String key = stmt.substring(0, Math.min(60, stmt.length()));

            if (num != null && seenNums.contains(num))
                continue;
            if (seen.contains(key))
                continue;

            if (num != null)
                seenNums.add(num);
            seen.add(key);
            result.add(q);
        }

        result.sort(Comparator.comparingInt(q -> {
            Object n = q.get("number");
            return n instanceof Number nb ? nb.intValue() : 999;
        }));

        return result;
    }

    // ── Persiste questões do template ─────────────────────────────────────
    private int persistTemplateQuestions(
            Long templateId,
            List<Map<String, Object>> questions) {

        int saved = 0;
        for (int i = 0; i < questions.size(); i++) {
            var q = questions.get(i);
            try {
                Object numObj = q.getOrDefault("number", i + 1);
                int num = numObj instanceof Number n
                        ? n.intValue()
                        : i + 1;

                jdbcClient.sql("""
                        INSERT INTO exam_template_questions
                            (template_id, original_number, original_text,
                             original_answer, topic_suggested,
                             discipline_suggested)
                        VALUES (:tid, :num, :stmt, :answer, :topic, :disc)
                        """)
                        .param("tid", templateId)
                        .param("num", num)
                        .param("stmt", q.get("statement"))
                        .param("answer", parseBoolean(q.get("answer")))
                        .param("topic", q.get("topic"))
                        .param("disc", q.get("discipline"))
                        .update();
                saved++;
            } catch (Exception e) {
                log.warn("Falha ao salvar questão #{}: {}", i, e.getMessage());
            }
        }
        return saved;
    }

    private void updateStatus(Long templateId, String status, String error) {
        jdbcClient.sql("""
                UPDATE exam_templates SET status = :status WHERE id = :id
                """)
                .param("status", status)
                .param("id", templateId)
                .update();
        log.error("Template {} → {}: {}", templateId, status, error);
    }

    private Boolean parseBoolean(Object val) {
        if (val == null)
            return null;
        if (val instanceof Boolean b)
            return b;
        String s = val.toString().toLowerCase();
        return s.equals("true") || s.equals("certo")
                || s.equals("c") || s.equals("v");
    }

    private String extractJsonArray(String text) {
        if (text == null)
            return "[]";
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        return (start >= 0 && end > start)
                ? text.substring(start, end + 1)
                : "[]";
    }

    // ── Métodos públicos (listTemplates, getTemplate etc.) ─────────────────

    public List<Map<String, Object>> listTemplates(Long contestId) {
        String filter = contestId != null
                ? "WHERE et.contest_id = :contestId "
                : "";
        var q = jdbcClient.sql("""
                SELECT et.id, et.name, et.year, et.role, et.status,
                       et.total_questions, et.created_at,
                       c.name AS contest_name
                FROM exam_templates et
                LEFT JOIN contests c ON et.contest_id = c.id
                """ + filter + "ORDER BY et.created_at DESC");

        if (contestId != null)
            q = q.param("contestId", contestId);

        return q.query((rs, n) -> {
            var m = new LinkedHashMap<String, Object>();
            m.put("id", rs.getLong("id"));
            m.put("name", rs.getString("name"));
            m.put("year", rs.getObject("year"));
            m.put("role", rs.getString("role"));
            m.put("status", rs.getString("status"));
            m.put("totalQuestions", rs.getInt("total_questions"));
            m.put("contestName", rs.getString("contest_name"));
            m.put("createdAt", rs.getTimestamp("created_at"));
            return (Map<String, Object>) m;
        }).list();
    }

    public Map<String, Object> getTemplate(Long templateId) {
        var template = jdbcClient.sql("""
                SELECT et.*, c.name AS contest_name
                FROM exam_templates et
                LEFT JOIN contests c ON et.contest_id = c.id
                WHERE et.id = :id
                """)
                .param("id", templateId)
                .query((rs, n) -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("year", rs.getObject("year"));
                    m.put("role", rs.getString("role"));
                    m.put("status", rs.getString("status"));
                    m.put("totalQuestions", rs.getInt("total_questions"));
                    m.put("contestName", rs.getString("contest_name"));
                    return (Map<String, Object>) m;
                })
                .optional()
                .orElseThrow(() -> new RuntimeException(
                        "Template não encontrado: " + templateId));

        var questions = jdbcClient.sql("""
                SELECT id, original_number, original_text,
                       original_answer, topic_suggested,
                       discipline_suggested, imported
                FROM exam_template_questions
                WHERE template_id = :tid
                ORDER BY original_number
                """)
                .param("tid", templateId)
                .query((rs, n) -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", rs.getLong("id"));
                    m.put("number", rs.getInt("original_number"));
                    m.put("statement", rs.getString("original_text"));
                    m.put("answer", rs.getObject("original_answer"));
                    m.put("topic", rs.getString("topic_suggested"));
                    m.put("discipline", rs.getString("discipline_suggested"));
                    m.put("imported", rs.getBoolean("imported"));
                    return (Map<String, Object>) m;
                })
                .list();

        template.put("questions", questions);
        return template;
    }

    public Map<String, Object> createSimulationFromTemplate(
            Long templateId, String mode, Long contestId) {

        var questions = jdbcClient.sql("""
                SELECT original_text, original_answer,
                       topic_suggested, discipline_suggested, question_id
                FROM exam_template_questions
                WHERE template_id = :tid
                  AND original_text IS NOT NULL
                ORDER BY original_number
                """)
                .param("tid", templateId)
                .query((rs, n) -> {
                    var m = new HashMap<String, Object>();
                    m.put("statement", rs.getString("original_text"));
                    m.put("answer", rs.getObject("original_answer"));
                    m.put("topic", rs.getString("topic_suggested"));
                    m.put("discipline", rs.getString("discipline_suggested"));
                    m.put("questionId", rs.getObject("question_id"));
                    return m;
                })
                .list();

        if ("ai_variant".equals(mode)) {
            String topics = questions.stream()
                    .map(q -> (String) q.getOrDefault("topic", ""))
                    .filter(t -> !t.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));

            String prompt = """
                    Com base nos tópicos desta prova Cebraspe, crie questões
                    similares com enunciados originais.
                    Tópicos: %s
                    Retorne JSON array no mesmo formato.
                    """.formatted(topics);

            var response = pipeline.process(
                    prompt, null, null,
                    "Especialista Cebraspe. Responda apenas JSON.");

            return Map.of(
                    "mode", "ai_variant",
                    "templateId", templateId,
                    "aiResponse", response.resposta(),
                    "message", "Variação gerada pela IA.");
        }

        return Map.of(
                "mode", "exact",
                "templateId", templateId,
                "questionCount", questions.size(),
                "questions", questions,
                "message", "Simulado baseado na prova original.");
    }

    public Map<String, Object> importQuestionsFromTemplate(
            Long templateId, Long topicId) {

        var rows = jdbcClient.sql("""
                SELECT id, original_text, original_answer
                FROM exam_template_questions
                WHERE template_id = :tid
                  AND imported    = FALSE
                  AND original_text IS NOT NULL
                """)
                .param("tid", templateId)
                .query((rs, n) -> {
                    var m = new HashMap<String, Object>();
                    m.put("id", rs.getLong("id"));
                    m.put("statement", rs.getString("original_text"));
                    m.put("answer", rs.getObject("original_answer"));
                    return m;
                })
                .list();

        if (topicId == null || rows.isEmpty()) {
            return Map.of("imported", 0, "total", rows.size(),
                    "message", "Selecione um tópico para importar.");
        }

        int imported = 0;
        for (var row : rows) {
            try {
                Boolean answer = parseBoolean(row.get("answer"));
                if (answer == null)
                    continue;

                var q = new Question(
                        null, topicId, null,
                        (String) row.get("statement"),
                        answer, null, null, null, null,
                        List.of(), null, "PROVA-IMPORTADA",
                        "MEDIO", true, true, null, null, null, null);

                var saved = questionRepository.save(q);

                jdbcClient.sql("""
                        UPDATE exam_template_questions
                        SET imported = TRUE, question_id = :qid
                        WHERE id = :id
                        """)
                        .param("qid", saved.id())
                        .param("id", row.get("id"))
                        .update();
                imported++;
            } catch (Exception e) {
                log.warn("Falha ao importar: {}", e.getMessage());
            }
        }

        return Map.of(
                "imported", imported,
                "total", rows.size(),
                "message", imported + " questões importadas.");
    }
}
