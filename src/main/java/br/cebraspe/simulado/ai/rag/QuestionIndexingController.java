package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.domain.question.QuestionIndexStatus;
import br.cebraspe.simulado.domain.question.QuestionRagIndexRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/questions")
public class QuestionIndexingController {

    private final QuestionIndexingService indexingService;
    private final QuestionRagIndexRepository indexRepository;

    public QuestionIndexingController(
            QuestionIndexingService indexingService,
            QuestionRagIndexRepository indexRepository) {
        this.indexingService = indexingService;
        this.indexRepository = indexRepository;
    }

    // ── Status de indexação de um concurso ───────────────────────────────
    @GetMapping("/status/{contestId}")
    public ResponseEntity<Map<String, Object>> getStatus(
            @PathVariable Long contestId) {

        var summary = indexRepository.getSummaryByContest(contestId);
        var statuses = indexRepository.findStatusByContest(contestId);

        return ResponseEntity.ok(Map.of(
                "summary", summary,
                "questions", statuses));
    }

    // ── Status de indexação de um tópico ─────────────────────────────────
    @GetMapping("/status/topic/{topicId}")
    public ResponseEntity<List<QuestionIndexStatus>> getStatusByTopic(
            @PathVariable Long topicId) {
        return ResponseEntity.ok(
                indexRepository.findStatusByTopic(topicId));
    }

    // ── Questões pendentes de indexação ──────────────────────────────────
    @GetMapping("/pending/{contestId}")
    public ResponseEntity<Map<String, Object>> getPending(
            @PathVariable Long contestId) {

        var pending = indexRepository.findPending(contestId);
        var summary = indexRepository.getSummaryByContest(contestId);

        return ResponseEntity.ok(Map.of(
                "pending", pending,
                "count", pending.size(),
                "summary", summary));
    }

    // ── Indexa questões pendentes de um concurso ─────────────────────────
    @PostMapping("/index/{contestId}")
    public ResponseEntity<QuestionIndexingService.IndexResult> indexByContest(
            @PathVariable Long contestId) {
        var result = indexingService.indexPendingByContest(contestId);
        return ResponseEntity.ok(result);
    }

    // ── Indexa questões de um tópico ─────────────────────────────────────
    @PostMapping("/index/topic/{topicId}")
    public ResponseEntity<QuestionIndexingService.IndexResult> indexByTopic(
            @PathVariable Long topicId) {
        var result = indexingService.indexByTopic(topicId);
        return ResponseEntity.ok(result);
    }

    // ── Indexa uma questão específica ─────────────────────────────────────
    @PostMapping("/index/question/{questionId}")
    public ResponseEntity<Map<String, Object>> indexOne(
            @PathVariable Long questionId) {
        return ResponseEntity.ok(indexingService.indexQuestion(questionId));
    }

    // ── Remove indexação (permite re-envio) ───────────────────────────────
    @DeleteMapping("/index/question/{questionId}")
    public ResponseEntity<Void> removeIndex(@PathVariable Long questionId) {
        indexingService.removeIndex(questionId);
        return ResponseEntity.ok().build();
    }
}