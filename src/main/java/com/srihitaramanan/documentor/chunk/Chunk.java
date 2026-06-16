package com.srihitaramanan.documentor.chunk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code chunks} table.
 *
 * <p>The embedding column is intentionally NOT mapped here — we read and
 * write it via native SQL in {@link ChunkRepository} because JPA has no
 * native support for {@code vector} types.
 */
@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Chunk() {}

    public Chunk(UUID id, UUID documentId, int chunkIndex, String content,
                 Integer pageNumber, Integer tokenCount, OffsetDateTime createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.pageNumber = pageNumber;
        this.tokenCount = tokenCount;
        this.createdAt = createdAt;
    }

    public static Chunk of(UUID documentId, int chunkIndex, String content,
                           Integer pageNumber, Integer tokenCount) {
        return new Chunk(
                UUID.randomUUID(), documentId, chunkIndex, content,
                pageNumber, tokenCount, OffsetDateTime.now()
        );
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public Integer getPageNumber() { return pageNumber; }
    public Integer getTokenCount() { return tokenCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chunk c)) return false;
        return id != null && id.equals(c.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}