package br.cebraspe.simulado.ai.rag;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagSearchService {

    private final KnowledgeRepository knowledgeRepository;

    public RagSearchService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    public String search(String query, Integer topK) {
        try {
            return knowledgeRepository.search(query, topK)
                    .stream()
                    .map(KnowledgeRepository.SearchResult::conteudo)
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            return "";
        }
    }

    public List<RagSearchResult> searchWithMetadata(String query, Integer topK) {
        try {
            return knowledgeRepository.search(query, topK)
                    .stream()
                    .map(r -> new RagSearchResult(
                            r.conteudo(),
                            r.id(),
                            r.fonte(),
                            r.similarity()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public record RagSearchResult(
            String content,
            Long   documentId,
            String source,
            Double score
    ) {}
}