package com.srihitaramanan.documentor.chunk;

import com.srihitaramanan.documentor.ingestion.EmbeddingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Searches for chunks most similar to a query embedding using pgvector
 * cosine similarity over the HNSW index.
 *
 * <p>Filtering by {@code userId} happens BEFORE ordering, so users only
 * ever see results from their own documents. This is enforced at the
 * SQL layer, not by trust-the-caller logic.
 */
@Service
public class VectorSearchService {

    @PersistenceContext
    private EntityManager em;

    public record ChunkHit(
            UUID id,
            UUID documentId,
            int chunkIndex,
            String content,
            Integer pageNumber,
            double similarity
    ) {}

    /**
     * Find the top-K chunks most similar to the query, scoped to one user
     * and optionally one document.
     *
     * @param queryEmbedding   the embedding vector, as a pgvector literal "[0.1,0.2,...]"
     * @param userId           required: limits the search to this user's documents
     * @param documentId       optional: if non-null, restricts to chunks of this one document
     * @param topK             number of results to return
     */
    @SuppressWarnings("unchecked")
    public List<ChunkHit> searchSimilar(
            String queryEmbedding,
            UUID userId,
            UUID documentId,
            int topK
    ) {
        String sql = """
            SELECT
                c.id,
                c.document_id,
                c.chunk_index,
                c.content,
                c.page_number,
                1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
            FROM chunks c
            JOIN documents d ON c.document_id = d.id
            WHERE d.user_id = :userId
              AND d.status = 'READY'
              AND (CAST(:documentId AS uuid) IS NULL OR d.id = CAST(:documentId AS uuid))
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """;

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("queryEmbedding", queryEmbedding)
                .setParameter("userId", userId)
                .setParameter("documentId", documentId)
                .setParameter("topK", topK)
                .getResultList();

        return rows.stream()
                .map(r -> new ChunkHit(
                        (UUID) r[0],
                        (UUID) r[1],
                        ((Number) r[2]).intValue(),
                        (String) r[3],
                        r[4] == null ? null : ((Number) r[4]).intValue(),
                        ((Number) r[5]).doubleValue()
                ))
                .toList();
    }
}