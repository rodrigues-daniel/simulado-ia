package br.cebraspe.simulado.ai;



import br.cebraspe.simulado.domain.question.QuestionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia-admin")
public class IAQuestionAdminController {

    private final IAQuestionAdminService service;
    private final QuestionRepository     questionRepository;

    public IAQuestionAdminController(IAQuestionAdminService service,
                                     QuestionRepository questionRepository) {
        this.service            = service;
        this.questionRepository = questionRepository;
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
}