package br.cebraspe.simulado.domain.simulation;

import br.cebraspe.simulado.domain.question.Question;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationService {

    private static final Logger log =
            LoggerFactory.getLogger(SimulationService.class);

    private final SimulationRepository simulationRepository;
    private final QuestionRepository   questionRepository;

    public SimulationService(SimulationRepository simulationRepository,
                             QuestionRepository questionRepository) {
        this.simulationRepository = simulationRepository;
        this.questionRepository   = questionRepository;
    }

    // ── Cria simulado recebendo IDs das questões já escolhidas ──────────
    // O frontend escolhe as questões via /questions/for-simulation
    // e passa os IDs aqui para persistir na simulation_questions
    @Transactional
    public Simulation createSimulation(Long contestId, String name,
                                       Integer questionCount,
                                       Integer timeLimitMin,
                                       List<Long> questionIds) {

        var simulation = new Simulation(
                null, contestId, name, questionCount,
                timeLimitMin, "PENDING", null, null, null
        );
        var saved = simulationRepository.save(simulation);

        // Persiste as questões escolhidas pelo frontend
        if (questionIds != null && !questionIds.isEmpty()) {
            simulationRepository.saveSimulationQuestionIds(
                    saved.id(), questionIds);
        }

        return saved;
    }

    // ── Calcula resultado final ─────────────────────────────────────────
    @Transactional
    public SimulationResult finishSimulation(Long simulationId,
                                             Map<Long, Boolean> answers) {

        simulationRepository.findById(simulationId)
                .orElseThrow(() -> new RuntimeException(
                        "Simulado não encontrado: " + simulationId));

        // Busca questões do simulado
        var simQuestions = simulationRepository
                .findSimulationQuestions(simulationId);

        if (simQuestions.isEmpty()) {
            // Fallback: se não salvou na tabela, usa os IDs do mapa de respostas
            log.warn("simulation_questions vazio para simulationId={}. " +
                     "Usando IDs do mapa de respostas.", simulationId);
            return calculateFromAnswerMap(simulationId, answers);
        }

        simulationRepository.updateStatus(
                simulationId, "FINISHED", LocalDateTime.now());

        int correct = 0, wrong = 0, skipped = 0;
        int answeredCount = 0;
        List<String> trapsHit = new ArrayList<>();

        for (var sq : simQuestions) {
            Long      questionId = sq.questionId();
            Boolean   userAnswer = answers.get(questionId);
            Question  question   = questionRepository
                    .findById(questionId).orElse(null);

            if (question == null) {
                log.warn("Questão {} não encontrada ao calcular resultado", questionId);
                skipped++;
                continue;
            }

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
                    // Detecta armadilhas quando marcou CERTO mas era ERRADO
                    if (Boolean.TRUE.equals(userAnswer)
                            && question.trapKeywords() != null) {
                        trapsHit.addAll(question.trapKeywords());
                    }
                }
                simulationRepository.updateAnswer(sq.id(), userAnswer, false);
            }
        }

        return buildAndSaveResult(
                simulationId, simQuestions.size(),
                answeredCount, correct, wrong, skipped, trapsHit);
    }

    // Fallback quando simulation_questions está vazio
    private SimulationResult calculateFromAnswerMap(Long simulationId,
                                                     Map<Long, Boolean> answers) {
        int correct = 0, wrong = 0, skipped = 0, answered = 0;
        List<String> trapsHit = new ArrayList<>();

        for (var entry : answers.entrySet()) {
            Long    questionId = entry.getKey();
            Boolean userAnswer = entry.getValue();
            var question = questionRepository.findById(questionId).orElse(null);

            if (question == null) continue;

            if (userAnswer == null) {
                skipped++;
            } else {
                answered++;
                boolean isCorrect = question.correctAnswer().equals(userAnswer);
                if (isCorrect) {
                    correct++;
                } else {
                    wrong++;
                    if (Boolean.TRUE.equals(userAnswer)
                            && question.trapKeywords() != null) {
                        trapsHit.addAll(question.trapKeywords());
                    }
                }
            }
        }

        simulationRepository.updateStatus(
                simulationId, "FINISHED", LocalDateTime.now());

        return buildAndSaveResult(simulationId, answers.size(),
                answered, correct, wrong, skipped, trapsHit);
    }

    private SimulationResult buildAndSaveResult(Long simulationId,
                                                 int total, int answered,
                                                 int correct, int wrong,
                                                 int skipped,
                                                 List<String> trapsHit) {
        // Nota líquida Cebraspe: certas - erradas (mínimo 0)
        BigDecimal grossScore = BigDecimal.valueOf(correct);
        BigDecimal netScore   = BigDecimal.valueOf(
                Math.max(0, correct - wrong));

        // Tendência de chute
        BigDecimal guessingPct = total > 0
                ? BigDecimal.valueOf(answered)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String riskLevel = calculateRiskLevel(correct, wrong, answered);

        // Remove duplicatas das armadilhas
        List<String> uniqueTraps = trapsHit.stream()
                .distinct()
                .collect(Collectors.toList());

        String reportJson = buildReportJson(
                correct, wrong, skipped, uniqueTraps);

        var result = new SimulationResult(
                null, simulationId, total, answered,
                correct, wrong, skipped,
                grossScore, netScore, guessingPct,
                riskLevel, uniqueTraps, reportJson,
                LocalDateTime.now()
        );

        return simulationRepository.saveResult(result);
    }

    private String calculateRiskLevel(int correct, int wrong, int answered) {
        if (answered == 0) return "NEUTRO";
        double errorRate = (double) wrong / answered;
        if (errorRate > 0.40) return "ALTO";
        if (errorRate > 0.25) return "MEDIO";
        return "BAIXO";
    }

    private String buildReportJson(int correct, int wrong,
                                    int skipped, List<String> traps) {
        String trapsJson = traps.stream()
                .map(t -> "\"" + t.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        return String.format(
                "{\"correct\":%d,\"wrong\":%d,\"skipped\":%d,\"trapsHit\":%s}",
                correct, wrong, skipped, trapsJson);
    }
}