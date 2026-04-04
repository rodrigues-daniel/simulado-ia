package br.cebraspe.simulado.domain.question;

import br.cebraspe.simulado.ai.QuestionGeneratorService;
import br.cebraspe.simulado.ai.ProfessorExplanationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

    private final QuestionService             questionService;
    private final QuestionGeneratorService    generatorService;
    private final QuestionRepository          questionRepository;
    private final ProfessorExplanationService professorExplanationService;
    private final QuestionNoteRepository      noteRepository;
    private final SavedQuestionRepository     savedRepository;

    public QuestionController(QuestionService questionService,
                              QuestionGeneratorService generatorService,
                              QuestionRepository questionRepository,
                              ProfessorExplanationService professorExplanationService,
                              QuestionNoteRepository noteRepository,
                              SavedQuestionRepository savedRepository) {
        this.questionService             = questionService;
        this.generatorService            = generatorService;
        this.questionRepository          = questionRepository;
        this.professorExplanationService = professorExplanationService;
        this.noteRepository              = noteRepository;
        this.savedRepository             = savedRepository;
    }

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<Question>> getByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(questionService.getQuestionsByTopic(topicId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Question> getById(@PathVariable Long id) {
        return questionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{questionId}/answer")
    public ResponseEntity<?> answer(@PathVariable Long questionId,
                                    @RequestBody AnswerRequest request) {
        try {
            return ResponseEntity.ok(questionService.processAnswer(
                    questionId, request.sessionId(),
                    request.answer(), request.timeSpentMs()));
        } catch (RuntimeException e) {
            log.error("Erro ao processar resposta questionId={}: {}",
                    questionId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<List<Question>> generate(@RequestBody GenerateRequest req) {
        return ResponseEntity.ok(
                generatorService.generateQuestions(req.topicId(), req.count()));
    }

    @PostMapping("/{questionId}/prefetch-explanation")
    public ResponseEntity<Map<String, Object>> prefetchExplanation(
            @PathVariable Long questionId) {
        return questionRepository.findById(questionId).map(question -> {
            try {
                if (question.explanation() != null
                        && question.explanation().trim().length() > 10) {
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "explanation", question.explanation(),
                            "source", "database"
                    ));
                }
                var result = professorExplanationService
                        .generateExplanation(question, !question.correctAnswer());
                return ResponseEntity.ok(Map.<String, Object>of(
                        "explanation", result.explanation(),
                        "source", result.source().name().toLowerCase()
                ));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.<String, Object>of(
                        "explanation", question.explanation() != null
                                ? question.explanation()
                                : "Revise o parágrafo indicado.",
                        "source", "fallback"
                ));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Favoritos ───────────────────────────────────────────────────────

    @PostMapping("/{questionId}/save")
    public ResponseEntity<Map<String, Object>> saveQuestion(
            @PathVariable Long questionId) {
        var saved = savedRepository.save(questionId);
        return ResponseEntity.ok(Map.of("saved", true, "id", saved.id()));
    }

    @DeleteMapping("/{questionId}/save")
    public ResponseEntity<Void> unsaveQuestion(@PathVariable Long questionId) {
        savedRepository.remove(questionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/saved")
    public ResponseEntity<List<SavedQuestionRepository.SavedQuestionDetail>> getSaved() {
        return ResponseEntity.ok(savedRepository.findAllWithDetails());
    }

    // ── Notas ───────────────────────────────────────────────────────────

    @GetMapping("/{questionId}/notes")
    public ResponseEntity<List<QuestionNoteRepository.QuestionNote>> getNotes(
            @PathVariable Long questionId) {
        return ResponseEntity.ok(noteRepository.findByQuestionId(questionId));
    }

    @PostMapping("/{questionId}/notes")
    public ResponseEntity<QuestionNoteRepository.QuestionNote> addNote(
            @PathVariable Long questionId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                noteRepository.save(questionId, body.get("note")));
    }

    @PutMapping("/notes/{noteId}")
    public ResponseEntity<QuestionNoteRepository.QuestionNote> updateNote(
            @PathVariable Long noteId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                noteRepository.update(noteId, body.get("note")));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long noteId) {
        noteRepository.delete(noteId);
        return ResponseEntity.ok().build();
    }

    // ── Simulado por fonte ──────────────────────────────────────────────

    @GetMapping("/for-simulation")
    public ResponseEntity<List<Question>> forSimulation(
            @RequestParam Long contestId,
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(defaultValue = "20") Integer limit) {

        return ResponseEntity.ok(switch (source) {
            case "IA"     -> questionRepository.findForSimulationBySource(
                    contestId, "IA-GERADA", limit);
            case "MANUAL" -> questionRepository.findForSimulationBySource(
                    contestId, "MANUAL", limit);
            default       -> questionRepository.findForSimulation(contestId, limit);
        });
    }

    public record AnswerRequest(Long sessionId, Boolean answer, Integer timeSpentMs) {}
    public record GenerateRequest(Long topicId, Integer count) {}
}