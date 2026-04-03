package br.cebraspe.simulado.domain.question;

import br.cebraspe.simulado.ai.ProfessorExplanationService;
import br.cebraspe.simulado.domain.pareto.ParetoService;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final ProfessorExplanationService professorExplanationService;
    private final ParetoService paretoService;

    public QuestionService(QuestionRepository questionRepository,
            ProfessorExplanationService professorExplanationService,
            ParetoService paretoService) {
        this.questionRepository = questionRepository;
        this.professorExplanationService = professorExplanationService;
        this.paretoService = paretoService;
    }

    public List<Question> getQuestionsByTopic(Long topicId) {
        return questionRepository.findByTopicId(topicId);
    }

    /**
     * Lógica do Estudo Inverso: ao errar, retorna o parágrafo específico
     * da lei vinculado à questão + explicação do professor virtual.
     */
    public AnswerResult processAnswer(Long questionId, Long sessionId,
            Boolean userAnswer, Integer timeSpentMs) {
        var question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada: " + questionId));

        boolean isCorrect = question.correctAnswer().equals(userAnswer);

        var answer = new QuestionAnswer(null, sessionId, questionId,
                userAnswer, isCorrect, LocalDateTime.now(), timeSpentMs);
        questionRepository.saveAnswer(answer);

        // Atualiza performance Pareto
        paretoService.updateUserPerformance(question.topicId(), isCorrect);

        String professorExplanation = null;
        String lawParagraph = null;

        if (!isCorrect) {
            // Estudo Inverso: entrega parágrafo específico, não o PDF inteiro
            lawParagraph = question.lawParagraph();
            professorExplanation = professorExplanationService
                    .generateExplanation(question, userAnswer);
        }

        return new AnswerResult(
                isCorrect,
                question.correctAnswer(),
                question.explanation(),
                professorExplanation,
                lawParagraph,
                question.lawReference(),
                question.trapKeywords(),
                question.professorTip());
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
            String professorTip) {
    }
}