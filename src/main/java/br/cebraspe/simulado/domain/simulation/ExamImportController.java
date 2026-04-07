package br.cebraspe.simulado.domain.simulation;

import br.cebraspe.simulado.ai.rag.ExamImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulations/exams")
public class ExamImportController {

    private final ExamImportService importService;

    public ExamImportController(ExamImportService importService) {
        this.importService = importService;
    }

    // ── Upload de prova em PDF ────────────────────────────────────────
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadExam(
            @RequestParam("file")       MultipartFile file,
            @RequestParam("name")       String name,
            @RequestParam(required = false) Long    contestId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String  role) throws Exception {

        var result = importService.importExam(
                file, name, contestId, year, role);
        return ResponseEntity.ok(result);
    }

    // ── Lista templates disponíveis ───────────────────────────────────
    @GetMapping("/templates")
    public ResponseEntity<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) Long contestId) {
        return ResponseEntity.ok(importService.listTemplates(contestId));
    }

    // ── Detalhe de um template ─────────────────────────────────────────
    @GetMapping("/templates/{templateId}")
    public ResponseEntity<Map<String, Object>> getTemplate(
            @PathVariable Long templateId) {
        return ResponseEntity.ok(importService.getTemplate(templateId));
    }

    // ── Cria simulado a partir do template ────────────────────────────
    @PostMapping("/templates/{templateId}/simulate")
    public ResponseEntity<Map<String, Object>> simulateFromTemplate(
            @PathVariable Long templateId,
            @RequestBody  SimulateFromTemplateRequest req) {
        return ResponseEntity.ok(
                importService.createSimulationFromTemplate(
                        templateId, req.mode(), req.contestId()));
    }

    // ── Importa questões do template para o banco ──────────────────────
    @PostMapping("/templates/{templateId}/import-questions")
    public ResponseEntity<Map<String, Object>> importQuestions(
            @PathVariable Long templateId,
            @RequestParam(required = false) Long topicId) {
        return ResponseEntity.ok(
                importService.importQuestionsFromTemplate(
                        templateId, topicId));
    }

    public record SimulateFromTemplateRequest(
            String mode,       // "exact" | "ai_variant"
            Long   contestId
    ) {}
}