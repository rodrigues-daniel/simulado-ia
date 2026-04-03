package br.cebraspe.simulado.domain.study;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/study")
public class StudySessionController {

    private final StudySessionService    sessionService;
    private final StudySessionRepository sessionRepository;

    public StudySessionController(StudySessionService sessionService,
                                  StudySessionRepository sessionRepository) {
        this.sessionService    = sessionService;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Cria sessão E retorna as questões do tópico.
     * Se não houver questões no banco, gera via IA automaticamente.
     * O frontend recebe tudo em uma única chamada.
     */
    @PostMapping("/sessions")
    public ResponseEntity<SessionStartResponse> createSession(
            @RequestBody Map<String, Long> body) {

        Long topicId = body.get("topicId");
        if (topicId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Cria a sessão
        Long sessionId = sessionRepository.create(topicId);

        // Busca ou gera questões
        var result = sessionService.getOrGenerateQuestions(topicId);

        String message = switch (result.source()) {
            case DATABASE      -> null; // silencioso — comportamento normal
            case AI_GENERATED  -> "Nenhuma questão encontrada no banco. " +
                    result.questions().size() +
                    " questões foram geradas pela IA para este tópico.";
            case AI_EMPTY      -> "A IA não conseguiu gerar questões para este tópico. " +
                    "Adicione questões manualmente no painel Admin.";
            case AI_ERROR      -> "Serviço de IA indisponível e não há questões no banco. " +
                    "Verifique se o Ollama está rodando ou adicione questões no Admin.";
        };

        return ResponseEntity.ok(new SessionStartResponse(
                sessionId,
                result.questions(),
                result.source().name(),
                message
        ));
    }

    @PatchMapping("/sessions/{id}/finish")
    public ResponseEntity<Void> finish(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> stats) {

        sessionRepository.finish(
                id,
                stats.getOrDefault("correct", 0),
                stats.getOrDefault("wrong",   0),
                stats.getOrDefault("skipped", 0)
        );
        return ResponseEntity.ok().build();
    }

    public record SessionStartResponse(
            Long sessionId,
            List<?> questions,
            String source,
            String message       // null = banco normal, string = aviso ao usuário
    ) {}
}