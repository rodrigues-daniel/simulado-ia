package br.cebraspe.simulado.ai;



import br.cebraspe.simulado.domain.question.QuestionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia-admin")
public class IAQuestionAdminController {

    private final IAQuestionAdminService    service;
    private final QuestionRepository        questionRepository;
    private final QuestionGeneratorService  questionGeneratorService; // ← adicione

    public IAQuestionAdminController(IAQuestionAdminService service,
                                     QuestionRepository questionRepository,
                                     QuestionGeneratorService questionGeneratorService) {
        this.service                   = service;
        this.questionRepository        = questionRepository;
        this.questionGeneratorService  = questionGeneratorService;
    }

    // ── Stats gerais ────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<QuestionRepository.IAQuestionStats> getStats() {
        return ResponseEntity.ok(service.getStats());
    }

    // ── Questões pendentes de revisão ───────────────────────────────────
    @GetMapping("/questions/pending")
    public ResponseEntity<List<QuestionRepository.IAQuestionSummary>> getPending() {
        return ResponseEntity.ok(service.getPendingReview());
    }

    @GetMapping("/questions/topic/{topicId}")
    public ResponseEntity<List<QuestionRepository.IAQuestionSummary>> getByTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(service.getByTopic(topicId));
    }

    // ── Aprovar ─────────────────────────────────────────────────────────
    @PostMapping("/questions/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        service.approve(id, body != null ? body.get("note") : null);
        return ResponseEntity.ok().build();
    }

    // ── Rejeitar ────────────────────────────────────────────────────────
    @PostMapping("/questions/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        service.reject(id, body != null ? body.get("note") : null);
        return ResponseEntity.ok().build();
    }

    // ── Deletar ─────────────────────────────────────────────────────────
    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }

    // ── Verificação de questões antes de estudar ────────────────────────
    @GetMapping("/topics/{topicId}/check")
    public ResponseEntity<IAQuestionAdminService.TopicQuestionCheck> checkTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(service.checkTopicQuestions(topicId));
    }

    // ── Qualidade RAG por tópico ────────────────────────────────────────
    @GetMapping("/rag/quality/{topicId}")
    public ResponseEntity<IAQuestionAdminService.RagQualityReport> ragQuality(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(service.analyzeRagQuality(topicId));
    }

    // ── Relatório global RAG ────────────────────────────────────────────
    @GetMapping("/rag/report/{contestId}")
    public ResponseEntity<IAQuestionAdminService.GlobalRagReport> ragReport(
            @PathVariable Long contestId) {
        return ResponseEntity.ok(service.getGlobalRagReport(contestId));
    }

    @PostMapping("/topics/{topicId}/generate")
    public ResponseEntity<Map<String, Object>> generateForTopic(
            @PathVariable Long topicId,
            @RequestBody GenerateRequest req) {
        try {
            var questions = questionGeneratorService.generateQuestions(
                    topicId, req.count());
            return ResponseEntity.ok(Map.of(
                    "generated", questions.size(),
                    "topicId",   topicId,
                    "message",   questions.size() + " questões geradas e aguardando revisão."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    public record GenerateRequest(Integer count) {}
}