package br.cebraspe.simulado.ai.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Serviço central de embeddings.
 * Garante uso consistente do mesmo modelo (nomic-embed-text)
 * tanto na ingestão quanto na busca.
 */
@Service
public class EmbeddingService {

    private static final Logger log =
            LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Gera embedding para um texto.
     * Retorna float[] compatível com pgvector.
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Texto vazio para embedding");
        }
        try {
            var response = embeddingModel.embedForResponse(List.of(text));
            var data     = response.getResults();
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("Embedding retornou vazio");
            }
            return data.get(0).getOutput();
        } catch (Exception e) {
            log.error("Erro ao gerar embedding: {}", e.getMessage());
            throw new RuntimeException("Falha no embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Converte float[] para String no formato pgvector: [0.1,0.2,...]
     */
    public String toVectorString(float[] embedding) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public String embedToString(String text) {
        return toVectorString(embed(text));
    }
}