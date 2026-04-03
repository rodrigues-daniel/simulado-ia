package br.cebraspe.simulado.domain.essay;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/essays")
public class EssaySkeletonController {

    private final EssaySkeletonService essaySkeletonService;
    private final EssaySkeletonRepository essaySkeletonRepository;

    public EssaySkeletonController(EssaySkeletonService essaySkeletonService,
            EssaySkeletonRepository essaySkeletonRepository) {
        this.essaySkeletonService = essaySkeletonService;
        this.essaySkeletonRepository = essaySkeletonRepository;
    }

    @PostMapping("/generate")
    public ResponseEntity<EssaySkeleton> generate(@RequestBody GenerateRequest req) {
        return ResponseEntity.ok(
                essaySkeletonService.generateSkeleton(req.topicId(), req.contestId()));
    }

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<EssaySkeleton>> getByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(essaySkeletonRepository.findByTopicId(topicId));
    }

    public record GenerateRequest(Long topicId, Long contestId) {
    }
}