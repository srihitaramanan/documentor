package com.srihitaramanan.documentor.ingestion;

import com.srihitaramanan.documentor.chunk.Chunk;
import com.srihitaramanan.documentor.chunk.ChunkRepository;
import com.srihitaramanan.documentor.document.Document;
import com.srihitaramanan.documentor.document.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the ingestion pipeline:
 *   extract text → chunk → embed → persist chunks
 *
 * <p>Runs asynchronously on a virtual-thread executor (see ADR-004).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final PdfTextExtractor extractor;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final int batchSize;

    public IngestionService(
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            PdfTextExtractor extractor,
            TextChunker chunker,
            EmbeddingService embeddingService,
            @Value("${documentor.ingestion.batch-size:32}") int batchSize
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.batchSize = batchSize;
    }

    /** Compute a SHA-256 hex digest for idempotent uploads. */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Run the full ingestion pipeline for a document. Runs asynchronously on
     * a virtual thread.
     */
    @Async
    @Transactional
    public void ingestAsync(UUID documentId, byte[] bytes) {
        log.info("Starting ingestion for document {}", documentId);

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document not found: " + documentId));

        try {
            doc.markProcessing();
            documentRepository.save(doc);

            // 1. Extract text
            var extracted = extractor.extract(bytes, doc.getFilename());
            log.info("Extracted {} chars from {} ({} pages)",
                    extracted.text().length(), doc.getFilename(), extracted.pageCount());

            // 2. Chunk
            List<TextChunker.TextChunk> textChunks = chunker.chunk(extracted.text());
            log.info("Split into {} chunks", textChunks.size());

            if (textChunks.isEmpty()) {
                throw new IngestionException("No chunks produced for " + doc.getFilename());
            }

            // 3. Embed in batches and persist
            int chunkIndex = 0;
            for (int i = 0; i < textChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, textChunks.size());
                List<TextChunker.TextChunk> batch = textChunks.subList(i, end);

                List<String> texts = batch.stream().map(TextChunker.TextChunk::content).toList();
                List<float[]> vectors = embeddingService.embedBatch(texts);

                for (int j = 0; j < batch.size(); j++) {
                    TextChunker.TextChunk tc = batch.get(j);
                    String embedding = EmbeddingService.toPgVectorLiteral(vectors.get(j));
                    chunkRepository.insertWithEmbedding(
                            UUID.randomUUID(),
                            doc.getId(),
                            chunkIndex++,
                            tc.content(),
                            null,                  // page tracking is a future enhancement
                            tc.tokenCount(),
                            embedding
                    );
                }
                log.info("Embedded and stored batch {}-{}", i, end - 1);
            }

            // 4. Mark ready
            doc.markReady(extracted.pageCount());
            documentRepository.save(doc);
            log.info("Ingestion complete for document {}", documentId);

        } catch (Exception e) {
            log.error("Ingestion failed for document {}: {}", documentId, e.getMessage(), e);
            doc.markFailed(e.getMessage());
            documentRepository.save(doc);
        }
    }
}