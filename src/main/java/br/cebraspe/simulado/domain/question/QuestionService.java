package br.cebraspe.simulado.domain.question;



import br.cebraspe.simulado.ai.ProfessorExplanationService;
import br.cebraspe.simulado.domain.pareto.ParetoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private final QuestionRepository questionRepository;
    private final ProfessorExplanationService professorExplanationService;
    private final ParetoService paretoService;

    public QuestionService(QuestionRepository questionRepository,
                           ProfessorExplanationService professorExplanationService,
                           ParetoService paretoService) {
        this.questionRepository          = questionRepository;
        this.professorExplanationService = professorExplanationService;
        this.paretoService               = paretoService;
    }

    public List<Question> getQuestionsByTopic(Long topicId) {
        return questionRepository.findByTopicId(topicId);
    }

    public AnswerResult processAnswer(Long questionId,
                                      Long sessionId,
                                      Boolean userAnswer,
                                      Integer timeSpentMs) {

        // ── 1. Busca questão ────────────────────────────────────────────
        var question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException(
                        "Questão não encontrada: " + questionId));

        boolean isCorrect = question.correctAnswer().equals(userAnswer);

        // ── 2. Persiste resposta ────────────────────────────────────────
        questionRepository.saveAnswer(new QuestionAnswer(
                null,
                sessionId,
                questionId,
                userAnswer,
                isCorrect,
                LocalDateTime.now(),
                timeSpentMs != null ? timeSpentMs : 0
        ));

        // ── 3. Atualiza Pareto — nunca derruba o fluxo ──────────────────
        try {
            paretoService.updateUserPerformance(question.topicId(), isCorrect);
        } catch (Exception e) {
            log.warn("Pareto update ignorado topicId={}: {}",
                    question.topicId(), e.getMessage());
        }

        // ── 4. IA apenas no erro ────────────────────────────────────────
        String professorExplanation = null;
        String lawParagraph         = null;

        if (!isCorrect) {
            lawParagraph = question.lawParagraph();
            try {
                professorExplanation = professorExplanationService
                        .generateExplanation(question, userAnswer);
            } catch (Exception e) {
                log.warn("IA indisponível para questionId={}: {}",
                        questionId, e.getMessage());
                professorExplanation = question.explanation() != null
                        ? question.explanation()
                        : "Revise o parágrafo da lei indicado acima.";
            }
        }

        // ── 5. Retorna sempre ───────────────────────────────────────────
        return new AnswerResult(
                isCorrect,
                question.correctAnswer(),
                question.explanation(),
                professorExplanation,
                lawParagraph,
                question.lawReference(),
                question.trapKeywords(),
                question.professorTip()
        );
    }

    public Question save(Question question) {
        return questionRepository.save(question);
    }

    public record AnswerResult(
            boolean isCorrect,
            boolean correctAnswer,
            String explanation,
            String professorExplanation,
            String lawParagraph,
            String lawReference,
            List<String> trapKeywords,
            String professorTip
    ) {}
}