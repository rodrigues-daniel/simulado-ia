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

    private final QuestionRepository          questionRepository;
    private final ProfessorExplanationService professorExplanationService;
    private final ParetoService               paretoService;
    private final SavedQuestionRepository     savedQuestionRepository;

    public QuestionService(QuestionRepository questionRepository,
                           ProfessorExplanationService professorExplanationService,
                           ParetoService paretoService,
                           SavedQuestionRepository savedQuestionRepository) {
        this.questionRepository          = questionRepository;
        this.professorExplanationService = professorExplanationService;
        this.paretoService               = paretoService;
        this.savedQuestionRepository     = savedQuestionRepository;
    }

    public List<Question> getQuestionsByTopic(Long topicId) {
        return questionRepository.findByTopicId(topicId);
    }

    public AnswerResult processAnswer(Long questionId, Long sessionId,
                                      Boolean userAnswer, Integer timeSpentMs) {

        var question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException(
                        "Questão não encontrada: " + questionId));

        boolean isCorrect = question.correctAnswer().equals(userAnswer);

        questionRepository.saveAnswer(new QuestionAnswer(
                null, sessionId, questionId, userAnswer, isCorrect,
                LocalDateTime.now(), timeSpentMs != null ? timeSpentMs : 0
        ));

        try {
            paretoService.updateUserPerformance(question.topicId(), isCorrect);
        } catch (Exception e) {
            log.warn("Pareto update ignorado topicId={}: {}",
                    question.topicId(), e.getMessage());
        }

        // Origem da questão
        boolean fromIA     = "IA-GERADA".equals(question.source());
        boolean isSaved    = savedQuestionRepository.isSaved(questionId);

        // Explicação só no erro
        String professorExplanation = null;
        String lawParagraph         = null;
        String explanationSource    = null;
        double ragScore             = 0.0;

        if (!isCorrect) {
            lawParagraph = question.lawParagraph();
            try {
                var result = professorExplanationService
                        .generateExplanation(question, userAnswer);
                professorExplanation = result.explanation();
                explanationSource    = result.source().name();
                ragScore             = result.ragScore();
            } catch (Exception e) {
                log.warn("Explicação falhou questionId={}: {}", questionId, e.getMessage());
                professorExplanation = question.explanation() != null
                        ? question.explanation()
                        : "Revise o parágrafo da lei indicado acima.";
                explanationSource = "FALLBACK";
            }
        }

        return new AnswerResult(
                isCorrect,
                question.correctAnswer(),
                question.explanation(),
                professorExplanation,
                lawParagraph,
                question.lawReference(),
                question.trapKeywords(),
                question.professorTip(),
                fromIA,
                explanationSource,
                ragScore,
                isSaved
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
            String professorTip,
            boolean fromIA,              // questão veio da IA
            String explanationSource,    // CACHE_RAG, AI_RAG, AI_ONLY, FALLBACK
            double ragScore,             // score médio do RAG
            boolean isSaved             // usuário já salvou esta questão
    ) {}
}