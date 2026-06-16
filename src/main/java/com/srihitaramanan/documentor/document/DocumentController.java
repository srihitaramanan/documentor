package com.srihitaramanan.documentor.document;

import com.srihitaramanan.documentor.chunk.ChunkRepository;
import com.srihitaramanan.documentor.ingestion.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "documents", description = "Upload and manage documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final IngestionService ingestionService;

    public DocumentController(
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            IngestionService ingestionService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestionService = ingestionService;
    }

    // ---- Upload ------------------------------------------------------------

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document for async ingestion")
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        byte[] bytes = file.getBytes();
        String contentHash = IngestionService.sha256Hex(bytes);

        // Idempotency: same user + same file content → return existing document
        var existing = documentRepository.findByUserIdAndContentHash(userId, contentHash);
        if (existing.isPresent()) {
            return ResponseEntity.ok(DocumentResponse.from(existing.get(), 0));
        }

        Document doc = Document.newPendingDocument(
                userId,
                file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename(),
                contentHash,
                bytes.length,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType()
        );
        documentRepository.save(doc);

        // Fire and forget — ingestion runs async on a virtual thread
        ingestionService.ingestAsync(doc.getId(), bytes);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(DocumentResponse.from(doc, 0));
    }

    // ---- List & Get --------------------------------------------------------

    @GetMapping
    @Operation(summary = "List the current user's documents")
    public List<DocumentResponse> list(@AuthenticationPrincipal UUID userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(d -> DocumentResponse.from(d, chunkRepository.countByDocumentId(d.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single document by id")
    public ResponseEntity<DocumentResponse> get(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id
    ) {
        return documentRepository.findByIdAndUserId(id, userId)
                .map(d -> ResponseEntity.ok(DocumentResponse.from(d, chunkRepository.countByDocumentId(d.getId()))))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and all its chunks")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id
    ) {
        return documentRepository.findByIdAndUserId(id, userId)
                .map(d -> {
                    documentRepository.delete(d);   // cascade deletes chunks via FK
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ---- Response DTO ------------------------------------------------------

    public record DocumentResponse(
            UUID id,
            String filename,
            DocumentStatus status,
            long fileSizeBytes,
            String mimeType,
            Integer pageCount,
            long chunkCount,
            String errorMessage,
            OffsetDateTime createdAt,
            OffsetDateTime processedAt
    ) {
        static DocumentResponse from(Document d, long chunkCount) {
            return new DocumentResponse(
                    d.getId(), d.getFilename(), d.getStatus(),
                    d.getFileSizeBytes(), d.getMimeType(),
                    d.getPageCount(), chunkCount, d.getErrorMessage(),
                    d.getCreatedAt(), d.getProcessedAt()
            );
        }
    }
}