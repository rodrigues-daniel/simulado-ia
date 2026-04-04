package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.ai.rag.cleaning.CleaningCacheRepository;
import br.cebraspe.simulado.ai.rag.cleaning.CleaningStrategy;
import br.cebraspe.simulado.ai.rag.cleaning.DataCleaningService;
import br.cebraspe.simulado.ai.rag.cleaning.TextCleaningResult;
import br.cebraspe.simulado.config.SystemConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagIngestionService    ingestionService;
    private final RagSearchService       searchService;
    private final RagRepository          ragRepository;
    private final RagQueueRepository     queueRepository;
    private final SystemConfigRepository configRepository;
    private final DataCleaningService cleaningService;
    private final CleaningCacheRepository cleaningCache;

    public RagController(RagIngestionService ingestionService,
                         RagSearchService searchService,
                         RagRepository ragRepository,
                         RagQueueRepository queueRepository,
                         SystemConfigRepository configRepository, DataCleaningService cleaningService, CleaningCacheRepository cleaningCache) {
        this.ingestionService = ingestionService;
        this.searchService    = searchService;
        this.ragRepository    = ragRepository;
        this.queueRepository  = queueRepository;
        this.configRepository = configRepository;
        this.cleaningService = cleaningService;
        this.cleaningCache = cleaningCache;
    }

    // ── Upload múltiplo (até 5 arquivos) ────────────────────────────────
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) Long    topicId,
            @RequestParam(required = false) Long    contestId,
            @RequestParam(required = false) Integer chunkSizeKb) {
        try {
            if (files.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Nenhum arquivo enviado."));
            }
            if (files.size() > 5) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Máximo de 5 arquivos por upload."));
            }

            var docs = ingestionService.ingestMultiple(
                    files, topicId, contestId, chunkSizeKb);

            return ResponseEntity.ok(Map.of(
                    "queued",    docs.size(),
                    "documents", docs,
                    "message",   docs.size() + " arquivo(s) enfileirado(s) para processamento."
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Erro ao processar: " + e.getMessage()));
        }
    }

    // ── Ingestão por texto ───────────────────────────────────────────────
    @PostMapping("/ingest-text")
    public ResponseEntity<?> ingestText(
            @RequestBody IngestTextRequest req) {
        try {
            var doc = ingestionService.ingestText(
                    req.name(), req.content(),
                    req.topicId(), req.contestId(),
                    req.chunkSizeKb());
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Busca semântica ──────────────────────────────────────────────────
    @GetMapping("/search")
    public ResponseEntity<List<RagSearchService.RagSearchResult>> search(
            @RequestParam String  query,
            @RequestParam(defaultValue = "5") Integer topK) {
        return ResponseEntity.ok(
                searchService.searchWithMetadata(query, topK));
    }

    // ── Lista documentos ─────────────────────────────────────────────────
    @GetMapping("/documents")
    public ResponseEntity<List<RagDocument>> listDocuments() {
        return ResponseEntity.ok(ragRepository.findAllDocuments());
    }

    // ── Status da fila ───────────────────────────────────────────────────
    @GetMapping("/queue")
    public ResponseEntity<List<RagQueueRepository.QueueItem>> getQueue() {
        return ResponseEntity.ok(queueRepository.findAll());
    }

    @GetMapping("/queue/stats")
    public ResponseEntity<RagQueueRepository.QueueStats> getQueueStats() {
        return ResponseEntity.ok(queueRepository.getStats());
    }

    @GetMapping("/queue/document/{documentId}")
    public ResponseEntity<List<RagQueueRepository.QueueItem>> getQueueByDocument(
            @PathVariable Long documentId) {
        return ResponseEntity.ok(
                queueRepository.findByDocumentId(documentId));
    }

    // ── Configuração de chunking ─────────────────────────────────────────
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "chunkSizeKb",    configRepository.getInt("rag.chunk.size.kb", 50),
                "chunkOverlapPct",configRepository.getInt("rag.chunk.overlap.pct", 10),
                "maxFiles",       configRepository.getInt("rag.max.files", 5)
        ));
    }

    @PutMapping("/config")
    public ResponseEntity<Void> saveConfig(
            @RequestBody Map<String, Integer> config) {
        if (config.containsKey("chunkSizeKb")) {
            configRepository.set("rag.chunk.size.kb",
                    String.valueOf(config.get("chunkSizeKb")));
        }
        if (config.containsKey("chunkOverlapPct")) {
            configRepository.set("rag.chunk.overlap.pct",
                    String.valueOf(config.get("chunkOverlapPct")));
        }
        return ResponseEntity.ok().build();
    }

    public record IngestTextRequest(
            String name, String content,
            Long topicId, Long contestId,
            Integer chunkSizeKb
    ) {}

    @PostMapping("/preview-cleaning")
    public ResponseEntity<TextCleaningResult> previewCleaning(
            @RequestParam("file") MultipartFile file) {
        try {
            ingestionService.validateFile(file);
            String content;
            if (file.getOriginalFilename() != null
                    && file.getOriginalFilename().endsWith(".pdf")) {
                // Para PDF, extrai só as primeiras 3 páginas como preview
                content = new String(file.getBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
            } else {
                content = new String(file.getBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            // Limita preview a 5000 chars
            String sample = content.length() > 5000
                    ? content.substring(0, 5000) : content;
            return ResponseEntity.ok(
                    cleaningService.clean(sample, file.getOriginalFilename()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Aplica estratégia específica de limpeza ──────────────────────────
    @PostMapping("/apply-cleaning")
    public ResponseEntity<TextCleaningResult> applyCleaning(
            @RequestBody ApplyCleaningRequest req) {
        var strategy = CleaningStrategy.valueOf(req.strategy());
        return ResponseEntity.ok(
                cleaningService.cleanWithStrategy(
                        req.content(), req.fileName(), strategy));
    }

    // ── Busca conteúdo aguardando revisão manual ─────────────────────────
    @GetMapping("/documents/{documentId}/cleaning")
    public ResponseEntity<?> getCleaningResult(@PathVariable Long documentId) {
        return cleaningCache.findByDocumentId(documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Salva edição manual e prossegue com vetorização ──────────────────
    @PostMapping("/documents/{documentId}/approve-cleaning")
    public ResponseEntity<Map<String, Object>> approveCleaning(
            @PathVariable Long documentId,
            @RequestBody ApproveCleaning req) {
        try {
            int chunkKb = configRepository.getInt("rag.chunk.size.kb", 50);
            ingestionService.processAfterManualReview(
                    documentId, req.editedContent(),
                    req.chunkSizeKb() != null ? req.chunkSizeKb() : chunkKb,
                    req.topicId());
            return ResponseEntity.ok(Map.of(
                    "status",  "PROCESSING",
                    "message", "Conteúdo aprovado. Vetorização iniciada."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    public record ApplyCleaningRequest(
            String content, String fileName, String strategy
    ) {}

    public record ApproveCleaning(
            String editedContent, Integer chunkSizeKb, Long topicId
    ) {}
}