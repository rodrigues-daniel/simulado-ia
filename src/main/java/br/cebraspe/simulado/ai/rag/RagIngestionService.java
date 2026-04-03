package br.cebraspe.simulado.ai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class RagIngestionService {

    private final VectorStore vectorStore;
    private final RagRepository ragRepository;

    @Value("${app.rag.chunk-size:500}")
    private Integer chunkSize;

    @Value("${app.rag.chunk-overlap:50}")
    private Integer chunkOverlap;

    public RagIngestionService(VectorStore vectorStore, RagRepository ragRepository) {
        this.vectorStore = vectorStore;
        this.ragRepository = ragRepository;
    }

    public RagDocument ingestPdf(MultipartFile file, Long topicId, Long contestId)
            throws IOException {
        // Persiste metadados do documento
        var ragDoc = new RagDocument(null, file.getOriginalFilename(),
                "PDF", topicId, contestId, null, 0, "PROCESSING", null);
        var saved = ragRepository.saveDocument(ragDoc);

        // Lê o PDF usando Spring AI
        var resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        var pdfReader = new PagePdfDocumentReader(resource);
        var rawDocs = pdfReader.get();

        // Divide em chunks
        TextSplitter splitter = new TokenTextSplitter(chunkSize, chunkOverlap,
                5, 10000, true);
        var chunks = splitter.apply(rawDocs);

        // Adiciona metadados para rastreabilidade
        chunks.forEach(chunk -> chunk.getMetadata().putAll(Map.of(
                "documentId", saved.id(),
                "topicId", topicId != null ? topicId : 0L,
                "source", file.getOriginalFilename())));

        // Persiste no PGVector via Spring AI
        vectorStore.add(chunks);

        // Registra chunks na tabela de metadados
        saveChunkMetadata(saved.id(), chunks);

        // Atualiza status e contagem
        ragRepository.updateDocumentStatus(saved.id(), "COMPLETED", chunks.size());

        return ragRepository.findDocumentById(saved.id()).orElseThrow();
    }

    public RagDocument ingestText(String name, String content,
            Long topicId, Long contestId) {
        var ragDoc = new RagDocument(null, name, "TEXT", topicId,
                contestId, null, 0, "PROCESSING", null);
        var saved = ragRepository.saveDocument(ragDoc);

        var doc = new Document(content, Map.of(
                "documentId", saved.id(),
                "topicId", topicId != null ? topicId : 0L,
                "source", name));

        TextSplitter splitter = new TokenTextSplitter(chunkSize, chunkOverlap,
                5, 10000, true);
        var chunks = splitter.apply(List.of(doc));

        vectorStore.add(chunks);
        saveChunkMetadata(saved.id(), chunks);
        ragRepository.updateDocumentStatus(saved.id(), "COMPLETED", chunks.size());

        return ragRepository.findDocumentById(saved.id()).orElseThrow();
    }

    private void saveChunkMetadata(Long documentId, List<Document> chunks) {

        for (int i = 0; i < chunks.size(); i++) {
            var chunk = new RagChunk(null, documentId, i,
                    chunks.get(i).getText(), null, null, null, null);
            ragRepository.saveChunk(chunk);
        }
    }
}