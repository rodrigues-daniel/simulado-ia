package br.cebraspe.simulado.ai.rag;

import br.cebraspe.simulado.ai.rag.cleaning.CleaningCacheRepository;
import br.cebraspe.simulado.ai.rag.cleaning.DataCleaningService;
import br.cebraspe.simulado.ai.rag.cleaning.TextCleaningResult;
import br.cebraspe.simulado.config.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class RagIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(RagIngestionService.class);

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "txt", "csv");

    private final KnowledgeRepository    knowledgeRepository; // ← substitui VectorStore
    private final RagRepository          ragRepository;
    private final RagQueueRepository     queueRepository;
    private final SystemConfigRepository configRepository;
    private final DataCleaningService    cleaningService;
    private final CleaningCacheRepository cleaningCache;

    public RagIngestionService(KnowledgeRepository knowledgeRepository,
                                RagRepository ragRepository,
                                RagQueueRepository queueRepository,
                                SystemConfigRepository configRepository,
                                DataCleaningService cleaningService,
                                CleaningCacheRepository cleaningCache) {
        this.knowledgeRepository = knowledgeRepository;
        this.ragRepository       = ragRepository;
        this.queueRepository     = queueRepository;
        this.configRepository    = configRepository;
        this.cleaningService     = cleaningService;
        this.cleaningCache       = cleaningCache;
    }

    // ── Validação ───────────────────────────────────────────────────────
    public void validateFile(MultipartFile file) {
        String ext = getExtension(
                file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "");
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                    "Formato não suportado: ." + ext +
                    ". Aceitos: .pdf, .txt, .csv");
        }
    }

    // ── Ingere múltiplos arquivos ───────────────────────────────────────
    public List<RagDocument> ingestMultiple(List<MultipartFile> files,
                                             Long topicId,
                                             Long contestId,
                                             Integer chunkSizeKb) {
        if (files.size() > 5) {
            throw new IllegalArgumentException("Máximo 5 arquivos por upload.");
        }
        if (chunkSizeKb != null && chunkSizeKb > 0) {
            configRepository.set("rag.chunk.size.kb",
                    String.valueOf(chunkSizeKb));
        }

        List<RagDocument> docs = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                validateFile(file);
                var doc = registerDocument(file, topicId, contestId);
                docs.add(doc);
                processAsync(doc.id(), file.getBytes(),
                        file.getOriginalFilename(),
                        resolveChunkSizeKb(chunkSizeKb),
                        topicId, contestId,
                        inferMateriaFromFile(file.getOriginalFilename()));
            } catch (Exception e) {
                log.error("Erro ao registrar {}: {}",
                        file.getOriginalFilename(), e.getMessage());
            }
        }
        return docs;
    }

    // ── Ingestão de texto direto ────────────────────────────────────────
    public RagDocument ingestText(String name, String content,
                                   Long topicId, Long contestId,
                                   Integer chunkSizeKb) {
        var ragDoc = new RagDocument(null, name, "TEXT",
                topicId, contestId, null, 0, "PROCESSING", null);
        var saved  = ragRepository.saveDocument(ragDoc);
        var qItem  = queueRepository.enqueue(saved.id(), name, 0, 1);

        try {
            processTextContent(saved.id(), qItem.id(), name, content,
                    resolveChunkSizeKb(chunkSizeKb),
                    topicId, contestId, null);
        } catch (Exception e) {
            queueRepository.updateError(qItem.id(), e.getMessage());
            ragRepository.updateDocumentStatus(saved.id(), "ERROR", 0);
        }
        return ragRepository.findDocumentById(saved.id()).orElseThrow();
    }

    // ── Processamento assíncrono ────────────────────────────────────────
    @Async
    public CompletableFuture<ProcessingResult> processAsync(
            Long documentId, byte[] fileBytes, String fileName,
            int chunkSizeKb, Long topicId, Long contestId, String materia) {

        var qItem = queueRepository.enqueue(documentId, fileName, 0, 1);

        try {
            queueRepository.updateStatus(qItem.id(), "PROCESSING");
            ragRepository.updateDocumentStatus(documentId, "PROCESSING", 0);

            // 1. Extrai texto bruto
            String rawText = "pdf".equals(getExtension(fileName))
                    ? extractPdfText(fileBytes, fileName)
                    : new String(fileBytes, StandardCharsets.UTF_8);

            // 2. Pipeline de limpeza
            var cleaningResult = cleaningService.clean(rawText, fileName);
            cleaningCache.saveCleanedContent(documentId,
                    cleaningResult.cleanedText(),
                    cleaningResult.strategyUsed().name(),
                    cleaningResult.confidenceScore(), false);

            // Baixa confiança → aguarda revisão manual
            if (cleaningResult.requiresManualReview()) {
                ragRepository.updateDocumentStatus(
                        documentId, "AWAITING_REVIEW", 0);
                queueRepository.updateStatus(
                        qItem.id(), "AWAITING_REVIEW");
                return CompletableFuture.completedFuture(
                        new ProcessingResult(documentId,
                                "AWAITING_REVIEW", cleaningResult, 0));
            }

            // 3. Chunking + indexação em conhecimento_estudo
            int chunks = chunkAndIndex(
                    cleaningResult.cleanedText(), fileName,
                    chunkSizeKb, documentId, topicId, contestId, materia);

            ragRepository.updateDocumentStatus(
                    documentId, "COMPLETED", chunks);
            queueRepository.updateStatus(qItem.id(), "COMPLETED");

            log.info("Ingestão concluída: {} → {} chunks indexados em " +
                     "conhecimento_estudo", fileName, chunks);

            return CompletableFuture.completedFuture(
                    new ProcessingResult(documentId, "COMPLETED",
                            cleaningResult, chunks));

        } catch (Exception e) {
            log.error("Erro ao processar {}: {}", fileName, e.getMessage(), e);
            queueRepository.updateError(qItem.id(), e.getMessage());
            ragRepository.updateDocumentStatus(documentId, "ERROR", 0);
            return CompletableFuture.completedFuture(
                    new ProcessingResult(documentId, "ERROR", null, 0));
        }
    }

    // ── Processa após revisão manual ─────────────────────────────────────
    public void processAfterManualReview(Long documentId,
                                          String editedContent,
                                          int chunkSizeKb, Long topicId) {
        var existing = cleaningCache.findByDocumentId(documentId);
        String strategy = existing.map(
                CleaningCacheRepository.CleanedDoc::strategyUsed)
                .orElse("GENERIC");

        cleaningCache.saveCleanedContent(
                documentId, editedContent, strategy, 1.0, true);

        var doc = ragRepository.findDocumentById(documentId).orElseThrow();
        ragRepository.updateDocumentStatus(documentId, "PROCESSING", 0);

        int chunks = chunkAndIndex(editedContent, doc.name(),
                chunkSizeKb, documentId, topicId, null, null);

        ragRepository.updateDocumentStatus(documentId, "COMPLETED", chunks);

        log.info("Revisão manual concluída: {} → {} chunks", doc.name(), chunks);
    }

    // ── Chunking + indexação em conhecimento_estudo ─────────────────────
    private int chunkAndIndex(String cleanedText, String fileName,
                               int chunkSizeKb, Long documentId,
                               Long topicId, Long contestId, String materia) {

        // Divide em chunks por tokens
        var splitter = buildSplitter(chunkSizeKb);
        var doc      = new org.springframework.ai.document.Document(cleanedText);
        var chunks   = splitter.apply(List.of(doc));

        String fonte    = fileName;
        String materiaF = materia != null ? materia
                        : inferMateriaFromContent(cleanedText);

        int saved = 0;
        for (var chunk : chunks) {
            try {
                // ── Indexa em conhecimento_estudo (pipeline RAG) ────────
                knowledgeRepository.save(
                        chunk.getText(),
                        materiaF,
                        topicId,
                        contestId,
                        fonte
                );

                // ── Salva metadado do chunk ─────────────────────────────
                ragRepository.saveChunk(new RagChunk(
                        null, documentId, saved,
                        chunk.getText(), null, null, null, null));

                saved++;
            } catch (Exception e) {
                log.warn("Chunk {}/{} falhou: {}", saved, chunks.size(),
                        e.getMessage());
            }
        }
        return saved;
    }

    // Síncrono — para ingestão de texto direto
    private void processTextContent(Long documentId, Long queueId,
                                     String name, String content,
                                     int chunkSizeKb,
                                     Long topicId, Long contestId,
                                     String materia) {
        queueRepository.updateStatus(queueId, "PROCESSING");
        int chunks = chunkAndIndex(content, name, chunkSizeKb,
                documentId, topicId, contestId, materia);
        ragRepository.updateDocumentStatus(documentId, "COMPLETED", chunks);
        queueRepository.updateStatus(queueId, "COMPLETED");
    }

    // ── Extrai texto de PDF ─────────────────────────────────────────────
    private String extractPdfText(byte[] bytes, String fileName) {
        var resource = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fileName; }
        };
        return new PagePdfDocumentReader(resource).get()
                .stream()
                .map(org.springframework.ai.document.Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    // ── Infere matéria pelo nome do arquivo ─────────────────────────────
    private String inferMateriaFromFile(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase();
        if (lower.contains("constituicao") || lower.contains("cf88")
                || lower.contains("art37")) return "Direito Constitucional";
        if (lower.contains("8112") || lower.contains("estatuto"))
            return "Direito Administrativo";
        if (lower.contains("lrf") || lower.contains("101"))
            return "Direito Financeiro";
        if (lower.contains("contabil") || lower.contains("pcasp"))
            return "Contabilidade Pública";
        if (lower.contains("auditoria"))
            return "Auditoria Governamental";
        return null;
    }

    // ── Infere matéria pelo conteúdo ────────────────────────────────────
    private String inferMateriaFromContent(String content) {
        if (content == null || content.length() < 100) return null;
        String sample = content.substring(0, Math.min(500, content.length()))
                .toLowerCase();
        if (sample.contains("art. 37") || sample.contains("constituição"))
            return "Direito Constitucional";
        if (sample.contains("lei 8.112") || sample.contains("processo administrativo"))
            return "Direito Administrativo";
        if (sample.contains("lei de responsabilidade") || sample.contains("lrf"))
            return "Direito Financeiro";
        if (sample.contains("pcasp") || sample.contains("mcasp"))
            return "Contabilidade Pública";
        return null;
    }

    private TokenTextSplitter buildSplitter(int chunkSizeKb) {
        int tokens  = Math.max(50, chunkSizeKb * 250);
        int overlap = Math.max(10, tokens / 10);
        return new TokenTextSplitter(tokens, overlap, 5, 100000, true);
    }

    private int resolveChunkSizeKb(Integer requested) {
        if (requested != null && requested > 0) return requested;
        return configRepository.getInt("rag.chunk.size.kb", 50);
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private RagDocument registerDocument(MultipartFile file,
                                          Long topicId, Long contestId) {
        String ext  = getExtension(file.getOriginalFilename());
        String type = "pdf".equals(ext) ? "PDF"
                    : "csv".equals(ext) ? "CSV" : "TEXT";
        var doc = new RagDocument(null, file.getOriginalFilename(),
                type, topicId, contestId, null, 0, "QUEUED", null);
        return ragRepository.saveDocument(doc);
    }

    public record ProcessingResult(
            Long documentId, String status,
            TextCleaningResult cleaningResult, int chunksCreated
    ) {}
}