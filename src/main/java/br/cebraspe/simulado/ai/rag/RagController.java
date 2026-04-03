package br.cebraspe.simulado.ai.rag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagIngestionService ingestionService;
    private final RagSearchService searchService;
    private final RagRepository ragRepository;

    public RagController(RagIngestionService ingestionService,
            RagSearchService searchService,
            RagRepository ragRepository) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
        this.ragRepository = ragRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<RagDocument> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Long contestId) throws Exception {
        return ResponseEntity.ok(ingestionService.ingestPdf(file, topicId, contestId));
    }

    @PostMapping("/ingest-text")
    public ResponseEntity<RagDocument> ingestText(@RequestBody IngestTextRequest req) {
        return ResponseEntity.ok(ingestionService.ingestText(
                req.name(), req.content(), req.topicId(), req.contestId()));
    }

    @GetMapping("/search")
    public ResponseEntity<List<RagSearchService.RagSearchResult>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") Integer topK) {
        return ResponseEntity.ok(searchService.searchWithMetadata(query, topK));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<RagDocument>> listDocuments() {
        return ResponseEntity.ok(ragRepository.findAllDocuments());
    }

    public record IngestTextRequest(
            String name, String content, Long topicId, Long contestId) {
    }
}