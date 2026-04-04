package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.ai.cache.SemanticCacheRepository;
import br.cebraspe.simulado.ai.pipeline.RagPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeRepository     knowledgeRepository;
    private final SemanticCacheRepository cacheRepository;
    private final RagPipelineService      pipeline;

    public KnowledgeAdminController(KnowledgeRepository knowledgeRepository,
                                     SemanticCacheRepository cacheRepository,
                                     RagPipelineService pipeline) {
        this.knowledgeRepository = knowledgeRepository;
        this.cacheRepository     = cacheRepository;
        this.pipeline            = pipeline;
    }

    // ── Adicionar conhecimento manual ───────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> addKnowledge(
            @RequestBody AddKnowledgeRequest req) {
        var id = knowledgeRepository.save(
                req.conteudo(), req.materia(),
                req.topicoId(), req.contestId(), req.fonte());
        return ResponseEntity.ok(Map.of(
                "id", id,
                "message", "Conhecimento indexado com sucesso."
        ));
    }

    // ── Bulk: adiciona múltiplos conhecimentos ──────────────────────────
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> addBulk(
            @RequestBody List<AddKnowledgeRequest> items) {
        int saved = 0;
        for (var req : items) {
            try {
                knowledgeRepository.save(req.conteudo(), req.materia(),
                        req.topicoId(), req.contestId(), req.fonte());
                saved++;
            } catch (Exception e) { /* continua */ }
        }
        return ResponseEntity.ok(Map.of(
                "saved", saved, "total", items.size()));
    }

    // ── Testar pipeline (busca + resposta) ──────────────────────────────
    @PostMapping("/test-pipeline")
    public ResponseEntity<PipelineTestResult> testPipeline(
            @RequestBody TestPipelineRequest req) {
        var start    = System.currentTimeMillis();
        var response = pipeline.process(
                req.pergunta(), req.materia(), req.topicoId());
        var elapsed  = System.currentTimeMillis() - start;

        return ResponseEntity.ok(new PipelineTestResult(
                response.resposta(),
                response.source().name(),
                response.cacheDistance(),
                response.ragChunksUsed(),
                response.contextoUsado(),
                elapsed
        ));
    }

    // ── Busca semântica na base ─────────────────────────────────────────
    @GetMapping("/search")
    public ResponseEntity<List<KnowledgeRepository.SearchResult>> search(
            @RequestParam String  query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String  materia,
            @RequestParam(required = false) Long    topicoId) {
        return ResponseEntity.ok(
                knowledgeRepository.search(query, topK, materia, topicoId));
    }

    // ── Estatísticas ─────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalChunks",  knowledgeRepository.count(),
                "cacheEntries", cacheRepository.count(),
                "cacheTopHits", cacheRepository.getTopHits(10)
        ));
    }

    // ── Listar por tópico ────────────────────────────────────────────────
    @GetMapping("/topic/{topicoId}")
    public ResponseEntity<List<KnowledgeRepository.KnowledgeChunk>> getByTopic(
            @PathVariable Long topicoId) {
        return ResponseEntity.ok(knowledgeRepository.findByTopico(topicoId));
    }

    // ── Deletar chunk ────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        knowledgeRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ── Limpar cache semântico ───────────────────────────────────────────
    @DeleteMapping("/cache/{id}")
    public ResponseEntity<Void> deleteCache(@PathVariable Long id) {
        cacheRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    public record AddKnowledgeRequest(
            String conteudo, String materia,
            Long topicoId, Long contestId, String fonte
    ) {}

    public record TestPipelineRequest(
            String pergunta, String materia, Long topicoId
    ) {}

    public record PipelineTestResult(
            String resposta, String source,
            double cacheDistance, int ragChunksUsed,
            String contextoUsado, long elapsedMs
    ) {}
}