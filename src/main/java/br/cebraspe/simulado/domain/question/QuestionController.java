package br.cebraspe.simulado.domain.question;

import br.cebraspe.simulado.ai.QuestionGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionGeneratorService generatorService;

    public QuestionController(QuestionService questionService,
            QuestionGeneratorService generatorService) {
        this.questionService = questionService;
        this.generatorService = generatorService;
    }

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<Question>> getByTopic(@PathVariable Long topicId) {
        return ResponseEntity.ok(questionService.getQuestionsByTopic(topicId));
    }

    @PostMapping("/{questionId}/answer")
    public ResponseEntity<QuestionService.AnswerResult> answer(
            @PathVariable Long questionId,
            @RequestBody AnswerRequest request) {

        var brackpoint = ResponseEntity.ok(questionService.processAnswer(
                questionId, request.sessionId(),
                request.answer(), request.timeSpentMs()));

        return brackpoint;
    }

    @PostMapping("/generate")
    public ResponseEntity<List<Question>> generate(@RequestBody GenerateRequest req) {
        return ResponseEntity.ok(
                generatorService.generateQuestions(req.topicId(), req.count()));
    }

    public record AnswerRequest(Long sessionId, Boolean answer, Integer timeSpentMs) {
    }

    public record GenerateRequest(Long topicId, Integer count) {
    }
}