package br.cebraspe.simulado.domain.pareto;

import br.cebraspe.simulado.domain.topic.TopicRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/pareto")
public class ParetoController {

    private final ParetoService paretoService;
    private final TopicRepository topicRepository;

    public ParetoController(ParetoService paretoService, TopicRepository topicRepository) {
        this.paretoService = paretoService;
        this.topicRepository = topicRepository;
    }

    @GetMapping("/dashboard/{contestId}")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @PathVariable Long contestId) {
        return ResponseEntity.ok(paretoService.getDashboardData(contestId));
    }

    @PostMapping("/recalculate/{contestId}")
    public ResponseEntity<Void> recalculate(@PathVariable Long contestId) {
        paretoService.autoHideLowRelevanceTopics(contestId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/topics/{topicId}/toggle-hidden")
    public ResponseEntity<Void> toggleHidden(@PathVariable Long topicId,
            @RequestParam boolean hidden) {
        topicRepository.toggleHidden(topicId, hidden);
        return ResponseEntity.ok().build();
    }
}