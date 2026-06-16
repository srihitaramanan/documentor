package com.srihitaramanan.documentor.ingestion;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token-aware sliding-window chunker.
 *
 * <p>Splits text into chunks of approximately {@code chunk-size-tokens}
 * tokens with {@code chunk-overlap-tokens} of overlap between adjacent
 * chunks. Prefers paragraph boundaries when possible. See ADR-005.
 */
@Component
public class TextChunker {

    private final int chunkSizeTokens;
    private final int overlapTokens;
    private final Encoding encoding;

    public TextChunker(
            @Value("${documentor.ingestion.chunk-size-tokens:500}") int chunkSizeTokens,
            @Value("${documentor.ingestion.chunk-overlap-tokens:50}") int overlapTokens
    ) {
        this.chunkSizeTokens = chunkSizeTokens;
        this.overlapTokens = overlapTokens;
        this.encoding = Encodings.newDefaultEncodingRegistry()
                .getEncoding(EncodingType.CL100K_BASE);
    }

    public record TextChunk(String content, int tokenCount) {}

    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Split on blank lines (paragraph boundaries)
        String[] paragraphs = text.split("\\n\\s*\\n");

        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            int pTokens = countTokens(trimmed);

            // If a single paragraph exceeds chunk size, split it into smaller pieces
            if (pTokens > chunkSizeTokens) {
                if (bufferTokens > 0) {
                    chunks.add(new TextChunk(buffer.toString().trim(), bufferTokens));
                    buffer = carryOverlap(buffer);
                    bufferTokens = countTokens(buffer.toString());
                }
                splitLargeParagraph(trimmed, chunks);
                continue;
            }

            // If adding this paragraph would overflow, emit current buffer first
            if (bufferTokens + pTokens > chunkSizeTokens && bufferTokens > 0) {
                chunks.add(new TextChunk(buffer.toString().trim(), bufferTokens));
                buffer = carryOverlap(buffer);
                bufferTokens = countTokens(buffer.toString());
            }

            if (!buffer.isEmpty()) buffer.append("\n\n");
            buffer.append(trimmed);
            bufferTokens += pTokens;
        }

        // Flush remaining buffer
        if (bufferTokens > 0) {
            chunks.add(new TextChunk(buffer.toString().trim(), bufferTokens));
        }

        return chunks;
    }

    /** Carry the last {@code overlapTokens} tokens forward into the next chunk. */
    private StringBuilder carryOverlap(StringBuilder prev) {
        IntArrayList tokens = encoding.encode(prev.toString());
        if (tokens.size() <= overlapTokens) {
            return new StringBuilder(prev);
        }
        IntArrayList tail = new IntArrayList();
        for (int i = tokens.size() - overlapTokens; i < tokens.size(); i++) {
            tail.add(tokens.get(i));
        }
        return new StringBuilder(encoding.decode(tail));
    }

    /** Fallback: paragraph is longer than chunk size, split by sentence. */
    private void splitLargeParagraph(String paragraph, List<TextChunk> chunks) {
        String[] sentences = paragraph.split("(?<=[.!?])\\s+");
        StringBuilder buf = new StringBuilder();
        int bufTokens = 0;
        for (String s : sentences) {
            int sTokens = countTokens(s);
            if (bufTokens + sTokens > chunkSizeTokens && bufTokens > 0) {
                chunks.add(new TextChunk(buf.toString().trim(), bufTokens));
                buf = new StringBuilder();
                bufTokens = 0;
            }
            if (!buf.isEmpty()) buf.append(" ");
            buf.append(s);
            bufTokens += sTokens;
        }
        if (bufTokens > 0) {
            chunks.add(new TextChunk(buf.toString().trim(), bufTokens));
        }
    }

    private int countTokens(String text) {
        return encoding.countTokens(text);
    }
}