package br.cebraspe.simulado.ai;


import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ChatModel chatModel;
    private final QuestionRepository questionRepository;
    private final ProfessorExplanationService professorExplanationService;

    public AiController(ChatModel chatModel,
                        QuestionRepository questionRepository,
                        ProfessorExplanationService professorExplanationService) {
        this.chatModel                   = chatModel;
        this.questionRepository          = questionRepository;
        this.professorExplanationService = professorExplanationService;
    }

    // Verificação leve se o Ollama está disponível
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            // Tenta uma chamada mínima ao modelo
            chatModel.call("ping");
            return ResponseEntity.ok(Map.of("status", "UP", "model", "llama3.2:3b"));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("status", "DOWN", "reason", e.getMessage()));
        }
    }

    // Pré-gera explicação para a questão sem registrar resposta
    // Chamado em background pelo prefetch do frontend
    @PostMapping("/questions/{questionId}/prefetch-explanation")
    public ResponseEntity<Map<String, Object>> prefetchExplanation(
            @PathVariable Long questionId) {

        return questionRepository.findById(questionId).map(question -> {
            try {
                // Se já tem explicação estática, retorna ela direto sem chamar IA
                if (question.explanation() != null
                        && question.explanation().trim().length() > 10) {
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "explanation", question.explanation(),
                            "source",      "database"
                    ));
                }

                // Gera via IA — assume que a questão é ERRADA para pré-gerar
                // (só faz sentido pré-carregar explicação de erro)
                String explanation = professorExplanationService
                        .generateExplanation(question, !question.correctAnswer());

                return ResponseEntity.ok(Map.<String, Object>of(
                        "explanation", explanation,
                        "source",      "ai"
                ));

            } catch (Exception e) {
                return ResponseEntity.ok(Map.<String, Object>of(
                        "explanation", question.explanation() != null
                                ? question.explanation()
                                : "Revise o parágrafo indicado.",
                        "source",      "fallback"
                ));
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}