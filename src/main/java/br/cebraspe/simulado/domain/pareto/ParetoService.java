package br.cebraspe.simulado.domain.pareto;

import br.cebraspe.simulado.domain.topic.TopicRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ParetoService {

    private final ParetoRepository paretoRepository;
    private final TopicRepository topicRepository;

    @Value("${app.pareto.threshold:0.70}")
    private BigDecimal paretoThreshold;

    public ParetoService(ParetoRepository paretoRepository,
            TopicRepository topicRepository) {
        this.paretoRepository = paretoRepository;
        this.topicRepository = topicRepository;
    }

    /**
     * Retorna tópicos prioritários (Pareto 80/20) com incidência >= 70%.
     * Tópicos de baixa relevância ficam "escondidos" do cronograma imediato.
     */
    public List<ParetoAnalysis> getPriorityTopics(Long contestId) {
        return paretoRepository.findTopByContest(contestId, paretoThreshold);
    }

    /**
     * Oculta automaticamente tópicos com incidência abaixo do threshold.
     * O usuário pode revelar manualmente quando quiser.
     */
    public void autoHideLowRelevanceTopics(Long contestId) {
        var allTopics = topicRepository.findByContestId(contestId);
        allTopics.forEach(topic -> {
            boolean shouldHide = topic.incidenceRate()
                    .compareTo(paretoThreshold) < 0;
            if (shouldHide != topic.isHidden()) {
                topicRepository.toggleHidden(topic.id(), shouldHide);
            }
        });
    }

    public void updateUserPerformance(Long topicId, boolean isCorrect) {
        paretoRepository.upsertUserPerformance(topicId, isCorrect);
    }

    public Map<String, Object> getDashboardData(Long contestId) {
        var priorityTopics = getPriorityTopics(contestId);
        var userPerformance = paretoRepository.getUserPerformanceByContest(contestId);
        var keywordPerformance = paretoRepository.getKeywordPerformance();

        return Map.of(
                "priorityTopics", priorityTopics,
                "userPerformance", userPerformance,
                "keywordTraps", keywordPerformance,
                "threshold", paretoThreshold);
    }

    // Recalcula Pareto toda noite à meia-noite
    @Scheduled(cron = "0 0 0 * * *")
    public void recalculateParetoScheduled() {
        paretoRepository.findAllContestIds()
                .forEach(this::autoHideLowRelevanceTopics);
    }
}