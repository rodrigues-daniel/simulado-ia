package br.cebraspe.simulado.domain.simulation;

import br.cebraspe.simulado.domain.question.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SimulationService {

    private final SimulationRepository simulationRepository;
    private final QuestionRepository questionRepository;

    public SimulationService(SimulationRepository simulationRepository,
            QuestionRepository questionRepository) {
        this.simulationRepository = simulationRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public Simulation createSimulation(Long contestId, String name,
            Integer questionCount, Integer timeLimitMin) {
        var simulation = new Simulation(null, contestId, name, questionCount,
                timeLimitMin, "PENDING", null, null, null);
        var saved = simulationRepository.save(simulation);

        // Seleciona questões priorizando tópicos Pareto
        var questions = questionRepository.findForSimulation(contestId, questionCount);
        simulationRepository.saveSimulationQuestions(saved.id(), questions);

        return saved;
    }

    @Transactional
    public SimulationResult finishSimulation(Long simulationId,
            Map<Long, Boolean> answers) {
        var simulation = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new RuntimeException("Simulado não encontrado"));

        var questions = simulationRepository.findSimulationQuestions(simulationId);
        simulationRepository.updateStatus(simulationId, "FINISHED", LocalDateTime.now());

        int correct = 0, wrong = 0, skipped = 0;
        List<String> trapsHit = new ArrayList<>();
        int answeredCount = 0;

        for (var sq : questions) {
            var question = questionRepository.findById(sq.questionId()).orElseThrow();
            var userAnswer = answers.get(sq.questionId());

            if (userAnswer == null) {
                skipped++;
                simulationRepository.updateAnswer(sq.id(), null, true);
            } else {
                answeredCount++;
                boolean isCorrect = question.correctAnswer().equals(userAnswer);
                if (isCorrect) {
                    correct++;
                } else {
                    wrong++;
                    // Detecta armadilhas de palavras-chave
                    if (question.trapKeywords() != null && userAnswer) {
                        trapsHit.addAll(question.trapKeywords());
                    }
                }
                simulationRepository.updateAnswer(sq.id(), userAnswer, false);
            }
        }

        // Cálculo Cebraspe: nota líquida = certas - erradas
        BigDecimal grossScore = BigDecimal.valueOf(correct);
        BigDecimal netScore = BigDecimal.valueOf(correct - wrong)
                .max(BigDecimal.ZERO);

        // Tendência de chute: % de respostas marcadas vs questões respondidas
        BigDecimal guessingPct = answeredCount > 0
                ? BigDecimal.valueOf(answeredCount)
                        .divide(BigDecimal.valueOf(questions.size()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        // Nível de risco: se errou > 30% das que respondeu = ALTO
        String riskLevel = calculateRiskLevel(correct, wrong, answeredCount);

        var result = new SimulationResult(null, simulationId,
                questions.size(), answeredCount, correct, wrong, skipped,
                grossScore, netScore, guessingPct, riskLevel,
                new ArrayList<>(new LinkedHashSet<>(trapsHit)),
                buildReportJson(correct, wrong, skipped, trapsHit),
                LocalDateTime.now());

        return simulationRepository.saveResult(result);
    }

    private String calculateRiskLevel(int correct, int wrong, int answered) {
        if (answered == 0)
            return "NEUTRO";
        double errorRate = (double) wrong / answered;
        if (errorRate > 0.4)
            return "ALTO";
        if (errorRate > 0.25)
            return "MEDIO";
        return "BAIXO";
    }

    private String buildReportJson(int correct, int wrong, int skipped,
            List<String> traps) {
        return """
                {"correct":%d,"wrong":%d,"skipped":%d,"trapsHit":%s}
                """.formatted(correct, wrong, skipped,
                traps.stream().map(t -> "\"" + t + "\"")
                        .collect(java.util.stream.Collectors.joining(",", "[", "]")));
    }
}