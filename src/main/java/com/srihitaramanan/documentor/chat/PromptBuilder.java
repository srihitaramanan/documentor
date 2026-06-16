package com.srihitaramanan.documentor.chat;

import com.srihitaramanan.documentor.chunk.VectorSearchService.ChunkHit;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds RAG prompts from retrieved chunks + question + recent conversation
 * history.
 *
 * <p>System message is hand-tuned to: (1) ground answers only in supplied
 * context, (2) say "I don't know" when context is insufficient, (3) cite
 * sources using a [chunk_id] notation we parse server-side.
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant that answers questions based ONLY on the
            document excerpts provided in the context below. Follow these rules:

            1. If the answer is in the context, give a clear, concise answer.
            2. If the answer is NOT in the context, say "I don't have enough
               information in the provided documents to answer that."
            3. Do not use outside knowledge.
            4. When you reference information, cite the source using the
               notation [chunk_id] where chunk_id matches an ID from the
               context section.
            5. Be honest about uncertainty.
            """;

    public record BuiltPrompt(String systemMessage, String userMessage) {}

    public BuiltPrompt build(String question, List<ChunkHit> chunks, List<Message> recentHistory) {
        StringBuilder user = new StringBuilder();

        if (recentHistory != null && !recentHistory.isEmpty()) {
            user.append("Recent conversation:\n");
            for (Message m : recentHistory) {
                user.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
            }
            user.append("\n");
        }

        if (chunks.isEmpty()) {
            user.append("Context: (no relevant excerpts found)\n\n");
        } else {
            user.append("Context (document excerpts):\n");
            for (ChunkHit c : chunks) {
                user.append("[")
                        .append(c.id())
                        .append("]");
                if (c.pageNumber() != null) {
                    user.append(" (page ").append(c.pageNumber()).append(")");
                }
                user.append(":\n")
                        .append(c.content())
                        .append("\n\n");
            }
        }

        user.append("Question: ").append(question);

        return new BuiltPrompt(SYSTEM_PROMPT, user.toString());
    }
}