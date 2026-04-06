package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.domain.question.Question;
import br.cebraspe.simulado.domain.question.QuestionIndexStatus;
import br.cebraspe.simulado.domain.question.QuestionRagIndexRepository;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import br.cebraspe.simulado.domain.topic.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class QuestionIndexingService {

    private static final Logger log = LoggerFactory.getLogger(QuestionIndexingService.class);

    private final QuestionRagIndexRepository indexRepository;
    private final QuestionRepository questionRepository;
    private final TopicRepository topicRepository;
    private final KnowledgeRepository knowledgeRepository;

    public QuestionIndexingService(
            QuestionRagIndexRepository indexRepository,
            QuestionRepository questionRepository,
            TopicRepository topicRepository,
            KnowledgeRepository knowledgeRepository) {
        this.indexRepository = indexRepository;
        this.questionRepository = questionRepository;
        this.topicRepository = topicRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    // ── Resultado de uma indexação ───────────────────────────────────────
    public record IndexResult(
            int totalProcessed,
            int indexed,
            int skipped,
            int failed,
            List<String> details) {
    }

    // ── Constrói o conteúdo textual da questão para indexação ────────────
    private String buildQuestionContent(Question q, String topicName,
            String discipline) {
        var sb = new StringBuilder();

        sb.append("QUESTÃO (Cebraspe — Certo/Errado)\n");
        if (topicName != null)
            sb.append("Tópico: ").append(topicName).append("\n");
        if (discipline != null)
            sb.append("Disciplina: ").append(discipline).append("\n");
        sb.append("\n");

        sb.append("ENUNCIADO:\n").append(q.statement()).append("\n\n");

        sb.append("GABARITO: ").append(q.correctAnswer() ? "CERTO" : "ERRADO").append("\n");

        if (q.lawReference() != null) {
            sb.append("BASE LEGAL: ").append(q.lawReference()).append("\n");
        }
        if (q.lawParagraph() != null) {
            sb.append("FUNDAMENTO:\n").append(q.lawParagraph()).append("\n");
        }
        if (q.explanation() != null) {
            sb.append("\nEXPLICAÇÃO:\n").append(q.explanation()).append("\n");
        }
        if (q.professorTip() != null) {
            sb.append("\nDICA DO PROFESSOR:\n").append(q.professorTip()).append("\n");
        }
        if (q.trapKeywords() != null && !q.trapKeywords().isEmpty()) {
            sb.append("\nPALAVRAS-ARMADILHA: ")
                    .append(String.join(", ", q.trapKeywords()))
                    .append("\n");
        }

        return sb.toString().trim();
    }

    // ── Calcula hash SHA-256 do conteúdo ─────────────────────────────────
    private String computeHash(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    // ── Indexa uma única questão ─────────────────────────────────────────
    public Map<String, Object> indexQuestion(Long questionId) {
        var question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException(
                        "Questão não encontrada: " + questionId));

        var topic = topicRepository.findById(question.topicId()).orElse(null);
        var topicName = topic != null ? topic.name() : null;
        var discipline = topic != null ? topic.discipline() : null;
        var materia = discipline;
        var fonte = buildFonte(question, topicName);

        String content = buildQuestionContent(question, topicName, discipline);
        String hash = computeHash(content);

        // Verifica se pode indexar
        if (!indexRepository.canIndex(questionId, hash)) {
            return Map.of(
                    "questionId", questionId,
                    "status", "SKIPPED",
                    "reason", "Conteúdo não modificado desde a última indexação.");
        }

        // Remove índice antigo se existir (para re-indexar)
        var existing = indexRepository.findByQuestionId(questionId);
        if (existing.isPresent()) {
            // Remove chunks antigos do conhecimento_estudo
            existing.get().knowledgeIds().forEach(kid -> {
                try {
                    knowledgeRepository.deleteById(kid);
                } catch (Exception e) {
                    log.warn("Falha ao remover chunk antigo {}: {}", kid, e.getMessage());
                }
            });
        }

        // Indexa no pipeline RAG
        try {
            Long knowledgeId = knowledgeRepository.save(
                    content, materia,
                    question.topicId(),
                    question.contestId(),
                    fonte);

            // Registra indexação
            indexRepository.saveIndex(
                    questionId, hash, 1,
                    List.of(knowledgeId),
                    "manual");

            log.info("Questão {} indexada no RAG (knowledgeId={})",
                    questionId, knowledgeId);

            return Map.of(
                    "questionId", questionId,
                    "status", "INDEXED",
                    "knowledgeId", knowledgeId,
                    "chunks", 1);

        } catch (Exception e) {
            log.error("Falha ao indexar questão {}: {}", questionId, e.getMessage());
            return Map.of(
                    "questionId", questionId,
                    "status", "FAILED",
                    "error", e.getMessage());
        }
    }

    // ── Indexa todas as questões pendentes de um concurso ────────────────
    public IndexResult indexPendingByContest(Long contestId) {
        var pending = indexRepository.findPending(contestId);

        if (pending.isEmpty()) {
            return new IndexResult(0, 0, 0, 0,
                    List.of("Nenhuma questão pendente de indexação."));
        }

        int indexed = 0, skipped = 0, failed = 0;
        List<String> details = new ArrayList<>();

        for (QuestionIndexStatus item : pending) {
            try {
                var result = indexQuestion(item.questionId());
                String status = (String) result.get("status");

                switch (status) {
                    case "INDEXED" -> {
                        indexed++;
                        details.add(
                                "✅ Questão #" + item.questionId() + " indexada.");
                    }
                    case "SKIPPED" -> {
                        skipped++;
                        details.add(
                                "⏭ Questão #" + item.questionId() + " ignorada (sem mudanças).");
                    }
                    default -> {
                        failed++;
                        details.add(
                                "❌ Questão #" + item.questionId() + ": " + result.get("error"));
                    }
                }
            } catch (Exception e) {
                failed++;
                details.add("❌ Questão #" + item.questionId() + ": " + e.getMessage());
                log.error("Falha ao indexar questão {}: {}",
                        item.questionId(), e.getMessage());
            }
        }

        log.info("Indexação concluída — total={} indexed={} skipped={} failed={}",
                pending.size(), indexed, skipped, failed);

        return new IndexResult(pending.size(), indexed, skipped, failed, details);
    }

    // ── Indexa todas as questões de um tópico ────────────────────────────
    public IndexResult indexByTopic(Long topicId) {
        var statuses = indexRepository.findStatusByTopic(topicId);
        var pending = statuses.stream()
                .filter(s -> !s.indexStatus().equals("INDEXED"))
                .toList();

        int indexed = 0, skipped = 0, failed = 0;
        List<String> details = new ArrayList<>();

        for (var item : pending) {
            var result = indexQuestion(item.questionId());
            String status = (String) result.get("status");
            switch (status) {
                case "INDEXED" -> indexed++;
                case "SKIPPED" -> skipped++;
                default -> failed++;
            }
            details.add(status + " — Questão #" + item.questionId());
        }

        return new IndexResult(pending.size(), indexed, skipped, failed, details);
    }

    // ── Remove indexação (força re-indexação no próximo envio) ────────────
    public void removeIndex(Long questionId) {
        var existing = indexRepository.findByQuestionId(questionId);
        existing.ifPresent(idx -> {
            idx.knowledgeIds().forEach(kid -> {
                try {
                    knowledgeRepository.deleteById(kid);
                } catch (Exception e) {
                    /* ignora */ }
            });
            indexRepository.removeIndex(questionId);
        });
        log.info("Índice removido para questão {}", questionId);
    }

    private String buildFonte(Question q, String topicName) {
        if (q.source() != null && !q.source().isBlank())
            return q.source();
        if (q.lawReference() != null)
            return q.lawReference();
        if (topicName != null)
            return "Banco de Questões — " + topicName;
        return "Banco de Questões";
    }
}