package com.srihitaramanan.documentor.chunk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

    List<Chunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    long countByDocumentId(UUID documentId);

    /**
     * Insert a chunk with its embedding using native SQL.
     *
     * <p>Hibernate doesn't know about pgvector's {@code vector} type, so we
     * cast a stringified array literal to {@code vector} server-side.
     * The {@code embedding} param must be formatted as {@code "[0.1,0.2,...]"}.
     */
    @Modifying
    @Query(value = """
            INSERT INTO chunks (id, document_id, chunk_index, content,
                                page_number, token_count, embedding, created_at)
            VALUES (:id, :documentId, :chunkIndex, :content,
                    :pageNumber, :tokenCount, CAST(:embedding AS vector), NOW())
            """, nativeQuery = true)
    void insertWithEmbedding(
            @Param("id") UUID id,
            @Param("documentId") UUID documentId,
            @Param("chunkIndex") int chunkIndex,
            @Param("content") String content,
            @Param("pageNumber") Integer pageNumber,
            @Param("tokenCount") Integer tokenCount,
            @Param("embedding") String embedding
    );
}