package br.cebraspe.simulado.ai;



import br.cebraspe.simulado.ai.rag.RagRepository;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import br.cebraspe.simulado.domain.topic.TopicRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class IAQuestionAdminService {

    // Mínimos recomendados para boa geração de questões
    private static final int    MIN_CHUNKS_GOOD     = 10;
    private static final int    MIN_CHUNKS_ACCEPTABLE = 5;
    private static final int    MIN_CHUNKS_POOR     = 1;
    private static final int    RECOMMENDED_CHUNKS  = 20;
    private static final double GOOD_COVERAGE       = 0.75;

    private final QuestionRepository questionRepository;
    private final RagRepository       ragRepository;
    private final TopicRepository     topicRepository;
    private final JdbcClient          jdbcClient;

    public IAQuestionAdminService(QuestionRepository questionRepository,
                                  RagRepository ragRepository,
                                  TopicRepository topicRepository,
                                  JdbcClient jdbcClient) {
        this.questionRepository = questionRepository;
        this.ragRepository      = ragRepository;
        this.topicRepository    = topicRepository;
        this.jdbcClient         = jdbcClient;
    }

    // ── Stats gerais de questões IA ─────────────────────────────────────
    public QuestionRepository.IAQuestionStats getStats() {
        return questionRepository.getIAStats();
    }

    // ── Questões pendentes de revisão ───────────────────────────────────
    public List<QuestionRepository.IAQuestionSummary> getPendingReview() {
        return questionRepository.findAllIAQuestionsUnreviewed();
    }

    public List<QuestionRepository.IAQuestionSummary> getByTopic(Long topicId) {
        return questionRepository.findIAQuestionsSummaryByTopic(topicId);
    }

    // ── Aprovar / Rejeitar questão ──────────────────────────────────────
    public void approve(Long questionId, String note) {
        questionRepository.reviewQuestion(questionId, true,
                note != null ? note : "Aprovada pelo administrador");
    }

    public void reject(Long questionId, String note) {
        questionRepository.reviewQuestion(questionId, false,
                note != null ? note : "Rejeitada pelo administrador");
    }

    public void delete(Long questionId) {
        questionRepository.deleteById(questionId);
    }

    // ── Verificação de quantidade antes de estudar ──────────────────────
    public TopicQuestionCheck checkTopicQuestions(Long topicId) {
        int total    = questionRepository.countByTopicId(topicId);
        int approved = questionRepository.countApprovedByTopicId(topicId);
        int ragChunks = getRagChunkCount(topicId);

        String recommendation = null;
        boolean suggestGenerate = false;

        if (total == 0) {
            recommendation  = "Este tópico não tem questões. A IA irá gerar automaticamente.";
            suggestGenerate = true;
        } else if (approved < 5) {
            recommendation  = "Poucas questões disponíveis (" + approved + "). " +
                    "Recomendamos gerar mais para melhor cobertura do tópico.";
            suggestGenerate = true;
        } else if (approved < 10) {
            recommendation  = "Cobertura básica (" + approved + " questões). " +
                    "Gerar mais questões aumenta a eficácia do estudo.";
            suggestGenerate = true;
        }

        return new TopicQuestionCheck(total, approved, ragChunks, suggestGenerate, recommendation);
    }

    // ── Análise de qualidade do RAG por tópico ──────────────────────────
    public RagQualityReport analyzeRagQuality(Long topicId) {
        int chunks = getRagChunkCount(topicId);
        var topic  = topicRepository.findById(topicId).orElse(null);

        String topicName   = topic != null ? topic.name() : "Tópico " + topicId;
        String qualityLevel;
        int    score;
        List<String> orientations = new ArrayList<>();
        List<String> missingTypes = new ArrayList<>();

        if (chunks == 0) {
            qualityLevel = "SEM_MATERIAL";
            score        = 0;
            orientations.add("⛔ Nenhum material foi ingerido para este tópico.");
            orientations.add("A IA irá gerar questões genéricas sem embasamento legal específico.");
            orientations.add("Acesse Admin → Material RAG e faça upload do PDF da lei ou apostila.");
            missingTypes.add("Texto da lei principal");
            missingTypes.add("Doutrina ou apostila");
            missingTypes.add("Questões comentadas anteriores");

        } else if (chunks < MIN_CHUNKS_ACCEPTABLE) {
            qualityLevel = "INSUFICIENTE";
            score        = 25;
            orientations.add("⚠️ Material insuficiente (" + chunks + " chunks de " + RECOMMENDED_CHUNKS + " recomendados).");
            orientations.add("Com menos de 5 chunks a IA não consegue embasar as questões na legislação correta.");
            orientations.add("Adicione pelo menos o texto integral da lei principal do tópico.");
            missingTypes.add("Artigos completos da lei (não apenas ementa)");
            missingTypes.add("Parágrafos e incisos relevantes");

        } else if (chunks < MIN_CHUNKS_GOOD) {
            qualityLevel = "BASICO";
            score        = 50;
            orientations.add("🟡 Material básico (" + chunks + " chunks). Questões terão qualidade parcial.");
            orientations.add("Adicione doutrina e comentários para a IA conseguir criar pegadinhas mais precisas.");
            orientations.add("Recomendado: " + RECOMMENDED_CHUNKS + " chunks para cobertura completa do tópico.");
            missingTypes.add("Comentários doutrinários");
            missingTypes.add("Jurisprudência aplicável");

        } else if (chunks < RECOMMENDED_CHUNKS) {
            qualityLevel = "ADEQUADO";
            score        = 75;
            orientations.add("🟢 Material adequado (" + chunks + " chunks). Boa base para geração.");
            orientations.add("Para cobertura completa, adicione mais " + (RECOMMENDED_CHUNKS - chunks) + " chunks.");
            orientations.add("Inclua questões comentadas de provas anteriores para calibrar o estilo Cebraspe.");

        } else {
            qualityLevel = "EXCELENTE";
            score        = 100;
            orientations.add("✅ Material excelente (" + chunks + " chunks). Geração de alta qualidade.");
            orientations.add("A IA tem embasamento suficiente para criar questões precisas no estilo Cebraspe.");
        }

        return new RagQualityReport(
                topicId, topicName, chunks, RECOMMENDED_CHUNKS,
                qualityLevel, score, orientations, missingTypes
        );
    }

    // ── Relatório global do RAG ─────────────────────────────────────────
    public GlobalRagReport getGlobalRagReport(Long contestId) {
        var topics = topicRepository.findByContestId(contestId);
        var reports = topics.stream()
                .map(t -> analyzeRagQuality(t.id()))
                .toList();

        long excellent   = reports.stream().filter(r -> r.qualityLevel().equals("EXCELENTE")).count();
        long adequate    = reports.stream().filter(r -> r.qualityLevel().equals("ADEQUADO")).count();
        long basic       = reports.stream().filter(r -> r.qualityLevel().equals("BASICO")).count();
        long insufficient= reports.stream().filter(r -> r.qualityLevel().equals("INSUFICIENTE")).count();
        long noMaterial  = reports.stream().filter(r -> r.qualityLevel().equals("SEM_MATERIAL")).count();

        double avgScore = reports.stream()
                .mapToInt(RagQualityReport::score)
                .average().orElse(0);

        List<String> globalOrientations = new ArrayList<>();

        if (noMaterial > 0) {
            globalOrientations.add("⛔ " + noMaterial + " tópico(s) sem nenhum material. " +
                    "Priorize ingerir o material destes tópicos primeiro.");
        }
        if (insufficient > 0) {
            globalOrientations.add("⚠️ " + insufficient + " tópico(s) com material insuficiente. " +
                    "Adicione pelo menos o texto da lei principal.");
        }
        if (basic + insufficient + noMaterial > reports.size() / 2) {
            globalOrientations.add("📄 Para o concurso completo, o ideal é ter pelo menos " +
                    (topics.size() * RECOMMENDED_CHUNKS) + " chunks totais no banco vetorial.");
        }
        if (adequate + excellent == reports.size()) {
            globalOrientations.add("✅ Todos os tópicos com material adequado. " +
                    "A geração de questões por IA está operando com alta qualidade.");
        }

        globalOrientations.add("💡 Dica: PDFs de leis completas rendem ~15-30 chunks. " +
                "Apostilas completas rendem 50-200 chunks dependendo do tamanho.");

        return new GlobalRagReport(
                reports, excellent, adequate, basic, insufficient, noMaterial,
                avgScore, globalOrientations
        );
    }

    private int getRagChunkCount(Long topicId) {
        try {
            return jdbcClient.sql("""
                    SELECT COUNT(*) FROM rag_chunks rc
                    JOIN rag_documents rd ON rc.document_id = rd.id
                    WHERE rd.topic_id = :topicId
                      AND rd.status   = 'COMPLETED'
                    """)
                    .param("topicId", topicId)
                    .query(Integer.class)
                    .single();
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Records de resultado ────────────────────────────────────────────
    public record TopicQuestionCheck(
            int total, int approved, int ragChunks,
            boolean suggestGenerate, String recommendation
    ) {}

    public record RagQualityReport(
            Long topicId, String topicName, int chunks, int recommended,
            String qualityLevel, int score,
            List<String> orientations, List<String> missingTypes
    ) {}

    public record GlobalRagReport(
            List<RagQualityReport> topicReports,
            long excellent, long adequate, long basic,
            long insufficient, long noMaterial,
            double avgScore, List<String> globalOrientations
    ) {}
}