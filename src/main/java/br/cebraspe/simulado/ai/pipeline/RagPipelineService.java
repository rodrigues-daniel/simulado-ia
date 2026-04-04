package br.cebraspe.simulado.ai.pipeline;

import br.cebraspe.simulado.ai.OllamaService;
import br.cebraspe.simulado.ai.cache.SemanticCacheRepository;
import br.cebraspe.simulado.ai.rag.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pipeline central de IA.
 *
 * Fluxo rigoroso:
 *  1. Cache semântico (distância < 0.1) → retorna imediatamente
 *  2. RAG: busca top-3 chunks em conhecimento_estudo
 *  3. Engenharia de prompt com contexto RAG
 *  4. Ollama gera resposta
 *  5. Salva pergunta + resposta no cache semântico
 */
@Service
public class RagPipelineService {

    private static final Logger log =
            LoggerFactory.getLogger(RagPipelineService.class);

    private final SemanticCacheRepository cacheRepository;
    private final KnowledgeRepository     knowledgeRepository;
    private final OllamaService           ollamaService;

    public RagPipelineService(SemanticCacheRepository cacheRepository,
                               KnowledgeRepository knowledgeRepository,
                               OllamaService ollamaService) {
        this.cacheRepository    = cacheRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.ollamaService      = ollamaService;
    }

    public record PipelineResponse(
            String resposta,
            ResponseSource source,
            double cacheDistance,   // 0 se não veio do cache
            int    ragChunksUsed,   // 0 se veio do cache
            String contextoUsado
    ) {}

    public enum ResponseSource {
        CACHE_SEMANTICO,   // respondeu do cache — economizou LLM
        RAG_OLLAMA,        // gerou com RAG + Ollama
        OLLAMA_ONLY,       // gerou só com Ollama (sem RAG)
        FALLBACK           // Ollama indisponível
    }

    /**
     * Ponto de entrada único do pipeline.
     * Todas as chamadas de IA do sistema devem passar por aqui.
     *
     * @param pergunta  texto da pergunta/prompt
     * @param materia   filtro opcional para RAG (ex: "Direito Administrativo")
     * @param topicoId  filtro opcional para RAG
     * @param systemCtx contexto de sistema adicional (papel do professor, etc.)
     */
    public PipelineResponse process(String pergunta, String materia,
                                     Long topicoId, String systemCtx) {

        // ── ETAPA 1: Cache Semântico ────────────────────────────────────
        var cacheHit = cacheRepository.findSimilar(pergunta);
        if (cacheHit.isPresent()) {
            var hit = cacheHit.get();
            log.info("Cache hit — distância={:.4f} hits={}",
                    hit.distance(), hit.hits());
            return new PipelineResponse(
                    hit.resposta(),
                    ResponseSource.CACHE_SEMANTICO,
                    hit.distance(), 0,
                    hit.contexto()
            );
        }

        // ── ETAPA 2: RAG — busca top-3 chunks ──────────────────────────
        List<KnowledgeRepository.SearchResult> chunks =
                List.of();
        String contexto = "";
        int chunksUsed  = 0;

        try {
            chunks = knowledgeRepository.search(
                    pergunta, 3, materia, topicoId);
            chunksUsed = chunks.size();

            if (!chunks.isEmpty()) {
                contexto = buildContext(chunks);
                log.debug("RAG: {} chunks encontrados (similaridade média: {:.3f})",
                        chunksUsed, avgSimilarity(chunks));
            }
        } catch (Exception e) {
            log.warn("RAG falhou, prosseguindo sem contexto: {}", e.getMessage());
        }

        // ── ETAPA 3: Engenharia de Prompt ───────────────────────────────
        String promptFinal = buildPrompt(pergunta, contexto, systemCtx);

        // ── ETAPA 4: Ollama gera resposta ───────────────────────────────
        String resposta;
        ResponseSource source;

        try {
            resposta = contexto.isBlank()
                    ? ollamaService.chat(promptFinal)
                    : ollamaService.chatWithContext(promptFinal, contexto);
            source = contexto.isBlank()
                    ? ResponseSource.OLLAMA_ONLY
                    : ResponseSource.RAG_OLLAMA;
        } catch (Exception e) {
            log.warn("Ollama indisponível: {}", e.getMessage());
            return new PipelineResponse(
                    "Serviço de IA temporariamente indisponível.",
                    ResponseSource.FALLBACK, 0, 0, ""
            );
        }

        // ── ETAPA 5: Salva no cache semântico ───────────────────────────
        try {
            cacheRepository.save(pergunta, resposta, contexto);
            log.debug("Cache salvo para pergunta: {}",
                    pergunta.substring(0, Math.min(50, pergunta.length())));
        } catch (Exception e) {
            log.warn("Falha ao salvar cache: {}", e.getMessage());
        }

        return new PipelineResponse(
                resposta, source, 0, chunksUsed, contexto);
    }

    // Sobrecarga sem contexto de sistema
    public PipelineResponse process(String pergunta,
                                     String materia, Long topicoId) {
        return process(pergunta, materia, topicoId, null);
    }

    // Sobrecarga mínima
    public PipelineResponse process(String pergunta) {
        return process(pergunta, null, null, null);
    }

    // ── Construção do contexto RAG ──────────────────────────────────────
    private String buildContext(
            List<KnowledgeRepository.SearchResult> chunks) {
        return chunks.stream()
                .map(c -> String.format(
                        "[Fonte: %s | Similaridade: %.0f%%]\n%s",
                        c.fonte() != null ? c.fonte() : "Material de estudo",
                        c.similarity() * 100,
                        c.conteudo()
                ))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    // ── Engenharia de Prompt ────────────────────────────────────────────
    private String buildPrompt(String pergunta, String contexto,
                                String systemCtx) {
        var sb = new StringBuilder();

        if (systemCtx != null && !systemCtx.isBlank()) {
            sb.append(systemCtx).append("\n\n");
        }

        if (!contexto.isBlank()) {
            sb.append("""
                    === MATERIAL DE REFERÊNCIA ===
                    (Use as informações abaixo para embasar sua resposta.
                     Cite artigos e parágrafos quando disponível.)
                    
                    """);
            sb.append(contexto);
            sb.append("\n\n=== FIM DO MATERIAL ===\n\n");
        }

        sb.append("PERGUNTA: ").append(pergunta);

        return sb.toString();
    }

    private double avgSimilarity(
            List<KnowledgeRepository.SearchResult> chunks) {
        return chunks.stream()
                .mapToDouble(KnowledgeRepository.SearchResult::similarity)
                .average().orElse(0);
    }
}