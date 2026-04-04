package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.ai.rag.cleaning.CleaningCacheRepository;
import br.cebraspe.simulado.ai.rag.cleaning.DataCleaningService;
import br.cebraspe.simulado.ai.rag.cleaning.TextCleaningResult;
import br.cebraspe.simulado.config.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class RagIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(RagIngestionService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/csv"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "txt", "csv"
    );

    private final VectorStore            vectorStore;
    private final RagRepository          ragRepository;
    private final RagQueueRepository     queueRepository;
    private final SystemConfigRepository configRepository;
    private final DataCleaningService cleaningService;
    private final CleaningCacheRepository cleaningCache;

    public RagIngestionService(VectorStore vectorStore,
                               RagRepository ragRepository,
                               RagQueueRepository queueRepository,
                               SystemConfigRepository configRepository,
                               DataCleaningService cleaningService,
                               CleaningCacheRepository cleaningCache) {
        this.vectorStore      = vectorStore;
        this.ragRepository    = ragRepository;
        this.queueRepository  = queueRepository;
        this.configRepository = configRepository;
        this.cleaningService = cleaningService;
        this.cleaningCache = cleaningCache;
    }

    // ── Valida extensão do arquivo ──────────────────────────────────────
    public void validateFile(MultipartFile file) {
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String ext = name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1) : "";

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                    "Formato não suportado: ." + ext +
                    ". Aceitos: .pdf, .txt, .csv");
        }
    }

    // ── Ingere múltiplos arquivos, retorna status imediato ──────────────
    public List<RagDocument> ingestMultiple(List<MultipartFile> files,
                                             Long topicId,
                                             Long contestId,
                                             Integer chunkSizeKb) {
        if (files.size() > 5) {
            throw new IllegalArgumentException(
                    "Máximo de 5 arquivos por upload.");
        }

        // Salva chunk size como default se fornecido
        if (chunkSizeKb != null && chunkSizeKb > 0) {
            configRepository.set("rag.chunk.size.kb",
                    String.valueOf(chunkSizeKb));
        }

        List<RagDocument> documents = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                validateFile(file);
                var doc = registerDocument(file, topicId, contestId);
                documents.add(doc);

                // Processa assincronamente
                processAsync(doc.id(), file.getBytes(),
                        file.getOriginalFilename(),
                        resolveChunkSizeKb(chunkSizeKb),
                        topicId);

            } catch (Exception e) {
                log.error("Erro ao registrar arquivo {}: {}",
                        file.getOriginalFilename(), e.getMessage());
                // Continua com os próximos arquivos
            }
        }

        return documents;
    }

    // ── Ingestão de texto direto ────────────────────────────────────────
    public RagDocument ingestText(String name, String content,
                                   Long topicId, Long contestId,
                                   Integer chunkSizeKb) {
        var ragDoc = new RagDocument(null, name, "TEXT", topicId,
                contestId, null, 0, "PROCESSING", null);
        var saved = ragRepository.saveDocument(ragDoc);

        // Enfileira para processamento
        var qItem = queueRepository.enqueue(saved.id(), name, 0, 1);

        try {
            processTextContent(saved.id(), qItem.id(), name, content,
                    resolveChunkSizeKb(chunkSizeKb), topicId);
        } catch (Exception e) {
            queueRepository.updateError(qItem.id(), e.getMessage());
            ragRepository.updateDocumentStatus(saved.id(), "ERROR", 0);
        }

        return ragRepository.findDocumentById(saved.id()).orElseThrow();
    }

    // ── Registra documento no banco antes de processar ──────────────────
    private RagDocument registerDocument(MultipartFile file,
                                          Long topicId, Long contestId) {
        String ext = getExtension(file.getOriginalFilename());
        String type = "pdf".equals(ext) ? "PDF"
                    : "csv".equals(ext) ? "CSV" : "TEXT";

        var ragDoc = new RagDocument(null,
                file.getOriginalFilename(), type,
                topicId, contestId, null, 0, "QUEUED", null);
        return ragRepository.saveDocument(ragDoc);
    }

    // ── Processamento assíncrono ────────────────────────────────────────

    @Async
    public CompletableFuture<ProcessingResult> processAsync(Long documentId,
                                                            byte[] fileBytes,
                                                            String fileName,
                                                            int chunkSizeKb,
                                                            Long topicId) {
        var qItem = queueRepository.enqueue(documentId, fileName, 0, 1);

        try {
            queueRepository.updateStatus(qItem.id(), "PROCESSING");
            ragRepository.updateDocumentStatus(documentId, "PROCESSING", 0);

            String ext = getExtension(fileName);

            // ── 1. Extrai texto bruto ───────────────────────────────────────
            String rawText;
            if ("pdf".equals(ext)) {
                rawText = extractPdfText(fileBytes, fileName);
            } else {
                rawText = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
            }

            // ── 2. Pipeline de limpeza ──────────────────────────────────────
            var cleaningResult = cleaningService.clean(rawText, fileName);

            // Persiste resultado da limpeza
            cleaningCache.saveCleanedContent(
                    documentId,
                    cleaningResult.cleanedText(),
                    cleaningResult.strategyUsed().name(),
                    cleaningResult.confidenceScore(),
                    false
            );

            // Se confiança baixa → marca para revisão manual e para aqui
            if (cleaningResult.requiresManualReview()) {
                ragRepository.updateDocumentStatus(documentId, "AWAITING_REVIEW", 0);
                queueRepository.updateStatus(qItem.id(), "AWAITING_REVIEW");

                log.warn("Documento {} requer revisão manual. Confiança: {:.2f}",
                        fileName, cleaningResult.confidenceScore());

                return CompletableFuture.completedFuture(
                        new ProcessingResult(documentId, "AWAITING_REVIEW",
                                cleaningResult, 0)
                );
            }

            // ── 3. Chunking e vetorização ───────────────────────────────────
            var chunks = chunkAndVectorize(
                    cleaningResult.cleanedText(), fileName,
                    chunkSizeKb, documentId, topicId);

            ragRepository.updateDocumentStatus(documentId, "COMPLETED", chunks.size());
            queueRepository.updateStatus(qItem.id(), "COMPLETED");

            log.info("Documento {} concluído: {} chunks, confiança {:.2f}",
                    fileName, chunks.size(), cleaningResult.confidenceScore());

            return CompletableFuture.completedFuture(
                    new ProcessingResult(documentId, "COMPLETED",
                            cleaningResult, chunks.size())
            );

        } catch (Exception e) {
            log.error("Erro ao processar {}: {}", fileName, e.getMessage(), e);
            queueRepository.updateError(qItem.id(), e.getMessage());
            ragRepository.updateDocumentStatus(documentId, "ERROR", 0);
            return CompletableFuture.completedFuture(
                    new ProcessingResult(documentId, "ERROR", null, 0));
        }
    }

    // ── Extrai texto de PDF ─────────────────────────────────────────────
    private String extractPdfText(byte[] bytes, String fileName) {
        var resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fileName; }
        };
        var reader = new PagePdfDocumentReader(resource);
        return reader.get().stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    // ── Chunking + vetorização ───────────────────────────────────────────
    private List<org.springframework.ai.document.Document> chunkAndVectorize(
            String cleanedText, String fileName,
            int chunkSizeKb, Long documentId, Long topicId) {

        var doc = new org.springframework.ai.document.Document(
                cleanedText, Map.of(
                "documentId", documentId,
                "topicId",    topicId != null ? topicId : 0L,
                "source",     fileName
        )
        );
        var splitter = buildSplitter(chunkSizeKb);
        var chunks   = splitter.apply(List.of(doc));

        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }
        saveChunkMetadata(documentId, chunks);
        return chunks;
    }

    // ── Processa após revisão manual ─────────────────────────────────────
    public void processAfterManualReview(Long documentId, String editedContent,
                                         int chunkSizeKb, Long topicId) {
        // Salva conteúdo editado manualmente
        var existing = cleaningCache.findByDocumentId(documentId);
        String strategy = existing.map(c -> c.strategyUsed())
                .orElse("GENERIC");

        cleaningCache.saveCleanedContent(documentId, editedContent,
                strategy, 1.0, true);

        // Busca info do documento
        var doc = ragRepository.findDocumentById(documentId).orElseThrow();

        ragRepository.updateDocumentStatus(documentId, "PROCESSING", 0);

        var chunks = chunkAndVectorize(editedContent, doc.name(),
                chunkSizeKb, documentId, topicId);

        ragRepository.updateDocumentStatus(documentId, "COMPLETED", chunks.size());

        log.info("Documento {} processado após revisão manual: {} chunks",
                doc.name(), chunks.size());
    }

    public record ProcessingResult(
            Long documentId,
            String status,
            TextCleaningResult cleaningResult,
            int chunksCreated
    ) {}

    // ── Processa PDF ────────────────────────────────────────────────────
    private List<Document> processPdf(byte[] bytes, String fileName,
                                       int chunkSizeKb, Long documentId,
                                       Long topicId) {
        var resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fileName; }
        };

        var reader = new PagePdfDocumentReader(resource);
        var raw    = reader.get();
        var splitter = buildSplitter(chunkSizeKb);
        var chunks   = splitter.apply(raw);

        addMetadata(chunks, documentId, topicId, fileName);
        return chunks;
    }

    // ── Processa TXT / CSV ──────────────────────────────────────────────
    private List<Document> processText(String content, String fileName,
                                        int chunkSizeKb, Long documentId,
                                        Long topicId) {
        var doc      = new Document(content, Map.of(
                "source", fileName,
                "documentId", documentId
        ));
        var splitter = buildSplitter(chunkSizeKb);
        var chunks   = splitter.apply(List.of(doc));
        addMetadata(chunks, documentId, topicId, fileName);
        return chunks;
    }

    // Processa texto de ingestão direta (síncrono)
    private void processTextContent(Long documentId, Long queueId,
                                     String name, String content,
                                     int chunkSizeKb, Long topicId) {
        queueRepository.updateStatus(queueId, "PROCESSING");
        var chunks = processText(content, name, chunkSizeKb, documentId, topicId);
        if (!chunks.isEmpty()) vectorStore.add(chunks);
        saveChunkMetadata(documentId, chunks);
        ragRepository.updateDocumentStatus(documentId, "COMPLETED", chunks.size());
        queueRepository.updateStatus(queueId, "COMPLETED");
    }

    // ── Splitter configurável por KB ────────────────────────────────────
    private TokenTextSplitter buildSplitter(int chunkSizeKb) {
        // Estimativa: 1KB ≈ 250 tokens (texto em português)
        int tokens  = Math.max(50, chunkSizeKb * 250);
        int overlap = Math.max(10, tokens / 10);
        return new TokenTextSplitter(tokens, overlap, 5, 100000, true);
    }

    private void addMetadata(List<Document> chunks, Long documentId,
                              Long topicId, String source) {
        chunks.forEach(c -> {
            c.getMetadata().put("documentId", documentId);
            c.getMetadata().put("topicId",    topicId != null ? topicId : 0L);
            c.getMetadata().put("source",     source);
        });
    }

    private void saveChunkMetadata(Long documentId, List<Document> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = new RagChunk(null, documentId, i,
                    chunks.get(i).getText(), null, null, null, null);
            try { ragRepository.saveChunk(chunk); }
            catch (Exception e) {
                log.warn("Falha ao salvar chunk metadata {}/{}: {}",
                        i, chunks.size(), e.getMessage());
            }
        }
    }

    private int resolveChunkSizeKb(Integer requested) {
        if (requested != null && requested > 0) return requested;
        return configRepository.getInt("rag.chunk.size.kb", 50);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}