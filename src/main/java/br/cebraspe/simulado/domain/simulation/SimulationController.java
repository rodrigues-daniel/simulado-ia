package br.cebraspe.simulado.domain.simulation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final SimulationService simulationService;
    private final SimulationRepository simulationRepository;

    public SimulationController(SimulationService simulationService,
            SimulationRepository simulationRepository) {
        this.simulationService = simulationService;
        this.simulationRepository = simulationRepository;
    }

    @PostMapping
    public ResponseEntity<Simulation> create(@RequestBody CreateSimulationRequest req) {
        return ResponseEntity.ok(simulationService.createSimulation(
                req.contestId(), req.name(), req.questionCount(), req.timeLimitMin()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Simulation> getById(@PathVariable Long id) {
        return simulationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/questions")
    public ResponseEntity<?> getQuestions(@PathVariable Long id) {
        return ResponseEntity.ok(simulationRepository.findSimulationQuestions(id));
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<SimulationResult> finish(
            @PathVariable Long id,
            @RequestBody Map<Long, Boolean> answers) {
        return ResponseEntity.ok(simulationService.finishSimulation(id, answers));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<SimulationResult> getResult(@PathVariable Long id) {
        return simulationRepository.findResult(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record CreateSimulationRequest(
            Long contestId, String name,
            Integer questionCount, Integer timeLimitMin) {
    }
}