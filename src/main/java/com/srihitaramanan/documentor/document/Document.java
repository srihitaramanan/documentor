package com.srihitaramanan.documentor.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code documents} table.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    protected Document() {}

    private Document(UUID id, UUID userId, String filename, String contentHash,
                     long fileSizeBytes, String mimeType, DocumentStatus status,
                     OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.filename = filename;
        this.contentHash = contentHash;
        this.fileSizeBytes = fileSizeBytes;
        this.mimeType = mimeType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Document newPendingDocument(
            UUID userId, String filename, String contentHash,
            long fileSizeBytes, String mimeType
    ) {
        return new Document(
                UUID.randomUUID(), userId, filename, contentHash,
                fileSizeBytes, mimeType, DocumentStatus.PENDING,
                OffsetDateTime.now()
        );
    }

    // ---- State transitions ----
    public void markProcessing() { this.status = DocumentStatus.PROCESSING; }

    public void markReady(int pageCount) {
        this.status = DocumentStatus.READY;
        this.pageCount = pageCount;
        this.processedAt = OffsetDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = OffsetDateTime.now();
    }

    // ---- Getters ----
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getFilename() { return filename; }
    public String getContentHash() { return contentHash; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getMimeType() { return mimeType; }
    public DocumentStatus getStatus() { return status; }
    public Integer getPageCount() { return pageCount; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document d)) return false;
        return id != null && id.equals(d.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}