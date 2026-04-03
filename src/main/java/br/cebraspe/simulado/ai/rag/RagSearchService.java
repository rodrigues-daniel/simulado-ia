package br.cebraspe.simulado.ai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagSearchService {

    private final VectorStore vectorStore;

    public RagSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Busca semântica no banco vetorial.
     * Retorna os trechos mais relevantes como contexto para o LLM.
     */
    public String search(String query, Integer topK) {
        try {
            var results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(0.65)
                            .build());
            return formatContext(results);
        } catch (Exception e) {
            return "";
        }
    }

    public List<RagSearchResult> searchWithMetadata(String query, Integer topK) {
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(0.65)
                            .build())
                    .stream().map(doc -> new RagSearchResult(
                            doc.getText(),
                            (Long) doc.getMetadata().getOrDefault("documentId", 0L),
                            (String) doc.getMetadata().getOrDefault("source", ""),
                            doc.getScore()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String formatContext(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    public record RagSearchResult(
            String content,
            Long documentId,
            String source,
            Double score) {
    }
}