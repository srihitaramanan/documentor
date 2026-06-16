package com.srihitaramanan.documentor.chat;

import com.srihitaramanan.documentor.chunk.VectorSearchService;
import com.srihitaramanan.documentor.chunk.VectorSearchService.ChunkHit;
import com.srihitaramanan.documentor.ingestion.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the RAG query pipeline:
 *   embed question -> vector search -> build prompt -> call LLM -> persist
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearch;
    private final PromptBuilder promptBuilder;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatClient chatClient;
    private final String modelName;
    private final int topK;
    private final int historyDepth;

    public ChatService(
            EmbeddingService embeddingService,
            VectorSearchService vectorSearch,
            PromptBuilder promptBuilder,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.ollama.chat.options.model:llama3.2}") String modelName,
            @Value("${documentor.chat.top-k:5}") int topK,
            @Value("${documentor.chat.history-depth:4}") int historyDepth
    ) {
        this.embeddingService = embeddingService;
        this.vectorSearch = vectorSearch;
        this.promptBuilder = promptBuilder;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatClient = chatClientBuilder.build();
        this.modelName = modelName;
        this.topK = topK;
        this.historyDepth = historyDepth;
    }

    public record AnswerResult(
            String answer,
            List<Citation> citations,
            Integer promptTokens,
            Integer completionTokens,
            String modelUsed
    ) {}

    public record Citation(
            UUID chunkId,
            UUID documentId,
            Integer pageNumber,
            double similarity,
            String snippet
    ) {}

    /**
     * Ask a question in a conversation. Returns the answer plus citations
     * to the chunks used as context.
     */
    @Transactional
    public AnswerResult ask(Conversation conversation, String question) {
        log.info("Conversation {} - question received: {}", conversation.getId(), question);

        // 1. Embed the question
        float[] queryVector = embeddingService.embed(question);
        String queryLiteral = EmbeddingService.toPgVectorLiteral(queryVector);

        // 2. Vector search
        List<ChunkHit> hits = vectorSearch.searchSimilar(
                queryLiteral,
                conversation.getUserId(),
                conversation.getDocumentId(),
                topK
        );
        log.info("Retrieved {} chunks (top-K={})", hits.size(), topK);

        // 3. Recent conversation history (for continuity)
        List<Message> recent = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .skip(Math.max(0, messageRepository
                        .findByConversationIdOrderByCreatedAtAsc(conversation.getId()).size() - historyDepth))
                .toList();

        // 4. Persist the user message FIRST so it shows up in history if we crash
        Message userMsg = Message.userMessage(conversation.getId(), question);
        messageRepository.save(userMsg);

        // 5. Build the prompt
        PromptBuilder.BuiltPrompt built = promptBuilder.build(question, hits, recent);
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(built.systemMessage()),
                new UserMessage(built.userMessage())
        ));

        // 6. Call the LLM
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        String answerText = response.getResult().getOutput().getContent();

        Integer promptTokens = null, completionTokens = null;
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            promptTokens = usage.getPromptTokens() == null ? null : usage.getPromptTokens().intValue();
            completionTokens = usage.getGenerationTokens() == null ? null : usage.getGenerationTokens().intValue();
        }

        // 7. Persist the assistant message with citations
        List<UUID> citedIds = hits.stream().map(ChunkHit::id).toList();
        Message assistantMsg = Message.assistantMessage(
                conversation.getId(), answerText, citedIds,
                promptTokens, completionTokens, modelName
        );
        messageRepository.save(assistantMsg);

        // 8. Build response
        List<Citation> citations = hits.stream()
                .map(h -> new Citation(
                        h.id(),
                        h.documentId(),
                        h.pageNumber(),
                        h.similarity(),
                        snippet(h.content())
                ))
                .toList();

        log.info("Conversation {} - answer generated ({} chars)",
                conversation.getId(), answerText.length());

        return new AnswerResult(answerText, citations, promptTokens, completionTokens, modelName);
    }

    private static String snippet(String content) {
        if (content == null) return "";
        return content.length() <= 160 ? content : content.substring(0, 160) + "...";
    }
}