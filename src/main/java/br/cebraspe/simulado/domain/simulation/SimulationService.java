package br.cebraspe.simulado.domain.simulation;

import br.cebraspe.simulado.domain.question.QuestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationService {

    private static final Logger log =
            LoggerFactory.getLogger(SimulationService.class);

    private final SimulationRepository simulationRepository;
    private final QuestionRepository   questionRepository;
    private final JdbcClient           jdbcClient;
    private final ObjectMapper         objectMapper;

    public SimulationService(SimulationRepository simulationRepository,
                              QuestionRepository questionRepository,
                              JdbcClient jdbcClient,
                              ObjectMapper objectMapper) {
        this.simulationRepository = simulationRepository;
        this.questionRepository   = questionRepository;
        this.jdbcClient           = jdbcClient;
        this.objectMapper         = objectMapper;
    }

    @Transactional
    public Simulation createSimulation(Long contestId, String name,
                                        Integer questionCount,
                                        Integer timeLimitMin,
                                        List<Long> questionIds,
                                        String modality,
                                        Integer totalVacancies,
                                        Integer quotaVacancies,
                                        Double cutScoreAmpla,
                                        Double cutScoreQuota,
                                        Double pointsCorrect,
                                        Double pointsWrong) {

        var sim = new Simulation(
                null, contestId, name, questionCount, timeLimitMin,
                "PENDING", null, null, null,
                modality,
                totalVacancies,
                quotaVacancies,
                cutScoreAmpla  != null ? BigDecimal.valueOf(cutScoreAmpla)  : null,
                cutScoreQuota  != null ? BigDecimal.valueOf(cutScoreQuota)  : null,
                pointsCorrect  != null ? BigDecimal.valueOf(pointsCorrect)  : BigDecimal.ONE,
                pointsWrong    != null ? BigDecimal.valueOf(pointsWrong)    : BigDecimal.ONE
        );

        var saved = simulationRepository.save(sim);

        if (questionIds != null && !questionIds.isEmpty()) {
            simulationRepository.saveSimulationQuestionIds(
                    saved.id(), questionIds);
        }

        return saved;
    }

    @Transactional
    public SimulationResult finishSimulation(Long simulationId,
                                              Map<Long, Boolean> answers) {
        var simulation = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new RuntimeException(
                        "Simulado não encontrado: " + simulationId));

        var simQuestions = simulationRepository
                .findSimulationQuestions(simulationId);

        if (simQuestions.isEmpty()) {
            return calculateFromAnswerMap(simulationId, simulation, answers);
        }

        simulationRepository.updateStatus(
                simulationId, "FINISHED", LocalDateTime.now());

        int correct = 0, wrong = 0, skipped = 0, answeredCount = 0;
        List<String> trapsHit = new ArrayList<>();

        for (var sq : simQuestions) {
            var question   = questionRepository.findById(sq.questionId())
                    .orElse(null);
            var userAnswer = answers.get(sq.questionId());

            if (question == null) { skipped++; continue; }

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
                    if (Boolean.TRUE.equals(userAnswer)
                            && question.trapKeywords() != null) {
                        trapsHit.addAll(question.trapKeywords());
                    }
                }
                simulationRepository.updateAnswer(sq.id(), userAnswer, false);
            }
        }

        return buildAndSaveResult(simulationId, simulation,
                simQuestions.size(), answeredCount,
                correct, wrong, skipped, trapsHit);
    }

    private SimulationResult calculateFromAnswerMap(
            Long simulationId, Simulation simulation,
            Map<Long, Boolean> answers) {

        int correct = 0, wrong = 0, skipped = 0, answered = 0;
        List<String> trapsHit = new ArrayList<>();

        for (var entry : answers.entrySet()) {
            var question = questionRepository.findById(entry.getKey())
                    .orElse(null);
            if (question == null) continue;

            Boolean userAnswer = entry.getValue();
            if (userAnswer == null) {
                skipped++;
            } else {
                answered++;
                boolean isCorrect = question.correctAnswer().equals(userAnswer);
                if (isCorrect) correct++;
                else {
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

        return buildAndSaveResult(simulationId, simulation,
                answers.size(), answered, correct, wrong, skipped, trapsHit);
    }

    private SimulationResult buildAndSaveResult(
            Long simulationId, Simulation simulation,
            int total, int answered, int correct,
            int wrong, int skipped, List<String> trapsHit) {

        // Configuração de pontuação
        double ptsCorrect = simulation.pointsCorrect() != null
                ? simulation.pointsCorrect().doubleValue() : 1.0;
        double ptsWrong   = simulation.pointsWrong() != null
                ? simulation.pointsWrong().doubleValue()   : 1.0;

        // Notas de corte do concurso
        var cutScores  = getCutScores(simulation.contestId());
        Double cutAmpla = simulation.cutScoreAmpla() != null
                ? simulation.cutScoreAmpla().doubleValue()
                : cutScores.getOrDefault("ampla", null);
        Double cutQuota = simulation.cutScoreQuota() != null
                ? simulation.cutScoreQuota().doubleValue()
                : cutScores.getOrDefault("quota", null);

        int vacAmpla = simulation.totalVacancies() != null
                ? simulation.totalVacancies() : 0;
        int vacQuota = simulation.quotaVacancies() != null
                ? simulation.quotaVacancies() : 0;

        var calc = new ScoreCalculator(
                total, correct, wrong, skipped,
                ptsCorrect, ptsWrong,
                cutAmpla, cutQuota,
                vacAmpla, vacQuota
        );

        var uniqueTraps = trapsHit.stream()
                .distinct()
                .collect(Collectors.toList());

        String reportJson = buildReportJson(
                correct, wrong, skipped, uniqueTraps,
                calc.buildScenarios(),
                calc.cutScoreStatus(),
                calc.buildStrategies(),
                calc);

        var result = new SimulationResult(
                null, simulationId, total, answered,
                correct, wrong, skipped,
                BigDecimal.valueOf(calc.grossScore()),
                BigDecimal.valueOf(calc.netScore()),
                BigDecimal.valueOf(calc.guessRiskRate()),
                calc.riskLevel(),
                uniqueTraps,
                reportJson,
                LocalDateTime.now()
        );

        return simulationRepository.saveResult(result);
    }

    private Map<String, Double> getCutScores(Long contestId) {
        if (contestId == null) return Map.of();
        try {
            return jdbcClient.sql("""
                    SELECT modality, cut_score
                    FROM cut_scores
                    WHERE contest_id = :contestId
                    ORDER BY year DESC
                    LIMIT 4
                    """)
                    .param("contestId", contestId)
                    .query((rs, n) -> Map.entry(
                            rs.getString("modality").toLowerCase(),
                            rs.getDouble("cut_score")
                    ))
                    .list()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.warn("Falha ao buscar notas de corte: {}", e.getMessage());
            return Map.of();
        }
    }

    private String buildReportJson(int correct, int wrong, int skipped,
                                    List<String> traps,
                                    List<ScoreCalculator.Scenario> scenarios,
                                    ScoreCalculator.CutScoreStatus cutStatus,
                                    List<String> strategies,
                                    ScoreCalculator calc) {
        try {
            var report = new LinkedHashMap<String, Object>();
            report.put("correct",        correct);
            report.put("wrong",          wrong);
            report.put("skipped",        skipped);
            report.put("trapsHit",       traps);
            report.put("optimalBlank",   calc.optimalBlankCount());
            report.put("scenarios",      scenarios);
            report.put("cutScoreStatus", cutStatus);
            report.put("strategies",     strategies);
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.warn("Falha ao serializar report: {}", e.getMessage());
            return "{}";
        }
    }
}