package com.srihitaramanan.documentor.ingestion;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin wrapper around Spring AI's {@link EmbeddingModel}.
 *
 * <p>Generates embeddings for one or many texts and formats them for
 * pgvector's {@code vector} type (a JSON-style array literal).
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** Embed a single text. */
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    /** Embed multiple texts in one provider call (more efficient than looping). */
    public List<float[]> embedBatch(List<String> texts) {
        return embeddingModel.embed(texts);
    }

    /**
     * Format a float[] vector as the literal pgvector expects:
     * {@code "[0.1,-0.234,0.456,...]"} — no spaces, square brackets, comma-separated.
     */
    public static String toPgVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}