package br.cebraspe.simulado.admin;

import br.cebraspe.simulado.admin.payload.*;
import br.cebraspe.simulado.domain.contest.*;
import br.cebraspe.simulado.domain.question.*;
import br.cebraspe.simulado.domain.topic.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ContestRepository contestRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    public AdminController(ContestRepository contestRepository,
            TopicRepository topicRepository,
            QuestionRepository questionRepository) {
        this.contestRepository = contestRepository;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
    }

    // ── Bulk load: Concursos ──────────────────────────────────────────────
    @PostMapping("/contests/bulk")
    public ResponseEntity<List<Contest>> bulkContests(
            @RequestBody List<ContestBulkPayload> payloads) {
        var saved = payloads.stream().map(p -> contestRepository.save(
                new Contest(null, p.name(), p.organ(), p.role(),
                        p.year(), p.level(), p.isDefault(), null)))
                .toList();
        return ResponseEntity.ok(saved);
    }

    // ── Bulk load: Tópicos ────────────────────────────────────────────────
    @PostMapping("/topics/bulk")
    public ResponseEntity<List<Topic>> bulkTopics(
            @RequestBody List<TopicBulkPayload> payloads) {
        var saved = payloads.stream().map(p -> topicRepository.save(
                new Topic(null, p.contestId(), p.name(), p.discipline(),
                        p.lawReference(), p.incidenceRate(), true, false, null)))
                .toList();
        return ResponseEntity.ok(saved);
    }

    // ── Bulk load: Questões ───────────────────────────────────────────────
    @PostMapping("/questions/bulk")
    public ResponseEntity<List<Question>> bulkQuestions(
            @RequestBody List<QuestionBulkPayload> payloads) {
        var saved = payloads.stream().map(p -> questionRepository.save(
                new Question(null, p.topicId(), p.contestId(), p.statement(),
                        p.correctAnswer(), p.lawParagraph(), p.lawReference(),
                        p.explanation(), p.professorTip(), p.trapKeywords(),
                        p.year(), p.source(), p.difficulty(), null)))
                .toList();
        return ResponseEntity.ok(saved);
    }

    // ── Listagens ─────────────────────────────────────────────────────────
    @GetMapping("/contests")
    public ResponseEntity<List<Contest>> listContests() {
        return ResponseEntity.ok(contestRepository.findAll());
    }

    @GetMapping("/topics/{contestId}")
    public ResponseEntity<List<Topic>> listTopics(@PathVariable Long contestId) {
        return ResponseEntity.ok(topicRepository.findByContestId(contestId));
    }
}