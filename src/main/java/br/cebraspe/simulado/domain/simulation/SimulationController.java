package br.cebraspe.simulado.domain.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private static final Logger log =
            LoggerFactory.getLogger(SimulationController.class);

    private final SimulationService    simulationService;
    private final SimulationRepository simulationRepository;

    public SimulationController(SimulationService simulationService,
                                 SimulationRepository simulationRepository) {
        this.simulationService    = simulationService;
        this.simulationRepository = simulationRepository;
    }

    @PostMapping
    public ResponseEntity<Simulation> create(
            @RequestBody CreateSimulationRequest req) {
        return ResponseEntity.ok(simulationService.createSimulation(
                req.contestId(),
                req.name(),
                req.questionCount(),
                req.timeLimitMin(),
                req.questionIds(),
                req.modality(),
                req.totalVacancies(),
                req.quotaVacancies(),
                req.cutScoreAmpla(),
                req.cutScoreQuota(),
                req.pointsCorrect(),
                req.pointsWrong()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Simulation> getById(@PathVariable Long id) {
        return simulationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/questions")
    public ResponseEntity<List<SimulationRepository.SimulationQuestion>> getQuestions(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                simulationRepository.findSimulationQuestions(id));
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<?> finish(
            @PathVariable Long id,
            @RequestBody Map<Long, Boolean> answers) {
        try {
            return ResponseEntity.ok(
                    simulationService.finishSimulation(id, answers));
        } catch (Exception e) {
            log.error("Erro ao finalizar simulado {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<SimulationResult> getResult(@PathVariable Long id) {
        return simulationRepository.findResult(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record CreateSimulationRequest(
            Long       contestId,
            String     name,
            Integer    questionCount,
            Integer    timeLimitMin,
            List<Long> questionIds,
            String     modality,
            Integer    totalVacancies,
            Integer    quotaVacancies,
            Double     cutScoreAmpla,
            Double     cutScoreQuota,
            Double     pointsCorrect,
            Double     pointsWrong
    ) {}
}