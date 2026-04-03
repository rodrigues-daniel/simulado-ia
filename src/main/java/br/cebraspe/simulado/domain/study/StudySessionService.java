package br.cebraspe.simulado.domain.study;



import br.cebraspe.simulado.ai.QuestionGeneratorService;
import br.cebraspe.simulado.domain.question.Question;
import br.cebraspe.simulado.domain.question.QuestionRepository;
import br.cebraspe.simulado.domain.topic.TopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StudySessionService {

    private static final Logger log = LoggerFactory.getLogger(StudySessionService.class);

    private final QuestionRepository      questionRepository;
    private final QuestionGeneratorService generatorService;
    private final TopicRepository         topicRepository;
    private final StudySessionRepository  sessionRepository;

    public StudySessionService(QuestionRepository questionRepository,
                               QuestionGeneratorService generatorService,
                               TopicRepository topicRepository,
                               StudySessionRepository sessionRepository) {
        this.questionRepository  = questionRepository;
        this.generatorService    = generatorService;
        this.topicRepository     = topicRepository;
        this.sessionRepository   = sessionRepository;
    }

    /**
     * Retorna questões para o estudo do tópico.
     *
     * Regra:
     *  - Se existem questões no banco → retorna elas direto (sem IA)
     *  - Se o banco está vazio        → gera via IA e persiste antes de retornar
     */
    public QuestionsResult getOrGenerateQuestions(Long topicId) {
        List<Question> existing = questionRepository.findByTopicId(topicId);

        if (!existing.isEmpty()) {
            log.debug("Tópico {} tem {} questões no banco. Usando banco.", topicId, existing.size());
            return new QuestionsResult(existing, Source.DATABASE);
        }

        // Banco vazio — verifica se o tópico existe
        var topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Tópico não encontrado: " + topicId));

        log.info("Tópico {} ('{}') sem questões. Gerando via IA...", topicId, topic.name());

        try {
            List<Question> generated = generatorService.generateQuestions(topicId, 10);

            if (generated.isEmpty()) {
                log.warn("IA não gerou questões para tópico {}.", topicId);
                return new QuestionsResult(List.of(), Source.AI_EMPTY);
            }

            log.info("IA gerou {} questões para tópico {}.", generated.size(), topicId);
            return new QuestionsResult(generated, Source.AI_GENERATED);

        } catch (Exception e) {
            log.error("Falha ao gerar questões via IA para tópico {}: {}", topicId, e.getMessage());
            return new QuestionsResult(List.of(), Source.AI_ERROR);
        }
    }

    public enum Source {
        DATABASE,      // veio do banco
        AI_GENERATED,  // gerado pela IA agora
        AI_EMPTY,      // IA não retornou nada
        AI_ERROR       // IA falhou
    }

    public record QuestionsResult(List<Question> questions, Source source) {
        public boolean hasQuestions() { return !questions.isEmpty(); }
        public boolean fromDatabase() { return source == Source.DATABASE; }
        public boolean fromAI()       { return source == Source.AI_GENERATED; }
    }
}