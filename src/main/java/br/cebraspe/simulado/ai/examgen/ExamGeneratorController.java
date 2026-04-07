package br.cebraspe.simulado.ai.examgen;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exam-generator")
public class ExamGeneratorController {

    private final ExamGenRepository    examRepo;
    private final ExamGeneratorService generatorService;

    public ExamGeneratorController(ExamGenRepository examRepo,
                                    ExamGeneratorService generatorService) {
        this.examRepo         = examRepo;
        this.generatorService = generatorService;
    }

    // ── Templates ────────────────────────────────────────────────────────

    @PostMapping("/templates")
    public ResponseEntity<ExamGenTemplate> createTemplate(
            @RequestBody ExamGenTemplate template) {
        return ResponseEntity.ok(examRepo.saveTemplate(template));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<ExamGenTemplate>> listTemplates(
            @RequestParam(required = false) Long contestId) {
        return ResponseEntity.ok(examRepo.findAllTemplates(contestId));
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<ExamGenTemplate> getTemplate(
            @PathVariable Long id) {
        return examRepo.findTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        examRepo.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }

    // ── Geração ──────────────────────────────────────────────────────────

    @PostMapping("/templates/{templateId}/generate")
    public ResponseEntity<Map<String, Object>> generate(
            @PathVariable Long templateId) {
        var exam = generatorService.startGeneration(templateId);
        return ResponseEntity.ok(Map.of(
                "examId",    exam.id(),
                "name",      exam.name(),
                "status",    exam.status(),
                "message",   "Geração iniciada em background."
        ));
    }

    @GetMapping("/exams")
    public ResponseEntity<List<GeneratedExam>> listExams() {
        return ResponseEntity.ok(examRepo.findAllExams());
    }

    @GetMapping("/exams/{examId}")
    public ResponseEntity<GeneratedExam> getExam(
            @PathVariable Long examId) {
        return examRepo.findExamById(examId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exams/{examId}/questions")
    public ResponseEntity<List<GeneratedExamQuestion>> getQuestions(
            @PathVariable Long examId) {
        return ResponseEntity.ok(examRepo.findExamQuestions(examId));
    }

    @GetMapping("/exams/{examId}/answer-key")
    public ResponseEntity<List<Map<String, Object>>> getAnswerKey(
            @PathVariable Long examId) {
        return ResponseEntity.ok(examRepo.getAnswerKey(examId));
    }

    // ── Iniciar simulado a partir de prova gerada ─────────────────────────
    @PostMapping("/exams/{examId}/start-simulation")
    public ResponseEntity<Map<String, Object>> startSimulation(
            @PathVariable Long examId) {
        var exam      = examRepo.findExamById(examId)
                .orElseThrow(() -> new RuntimeException("Prova não encontrada"));
        var questions = examRepo.findExamQuestions(examId);

        if (questions.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Prova sem questões."));
        }

        return ResponseEntity.ok(Map.of(
                "examId",        examId,
                "name",          exam.name(),
                "totalQuestions",questions.size(),
                "questions",     questions,
                "message",       "Prova pronta para simulado."
        ));
    }
}