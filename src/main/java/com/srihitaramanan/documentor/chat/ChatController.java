package com.srihitaramanan.documentor.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "chat", description = "Conversations and Q&A over your documents")
public class ChatController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatService chatService;

    public ChatController(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ChatService chatService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatService = chatService;
    }

    // ---- Conversations ----------------------------------------------------

    @PostMapping
    @Operation(summary = "Create a new conversation")
    public ResponseEntity<ConversationResponse> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateConversationRequest req
    ) {
        Conversation conv = Conversation.newConversation(userId, req.documentId(), req.title());
        conversationRepository.save(conv);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ConversationResponse.from(conv));
    }

    @GetMapping
    @Operation(summary = "List the current user's conversations")
    public List<ConversationResponse> list(@AuthenticationPrincipal UUID userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "List messages in a conversation")
    public ResponseEntity<List<MessageResponse>> messages(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id
    ) {
        return conversationRepository.findByIdAndUserId(id, userId)
                .map(conv -> ResponseEntity.ok(
                        messageRepository.findByConversationIdOrderByCreatedAtAsc(conv.getId()).stream()
                                .map(MessageResponse::from)
                                .toList()
                ))
                .orElse(ResponseEntity.notFound().build());
    }

    // ---- Ask --------------------------------------------------------------

    @PostMapping("/{id}/ask")
    @Operation(summary = "Ask a question grounded in your documents")
    public ResponseEntity<AskResponse> ask(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody AskRequest req
    ) {
        return conversationRepository.findByIdAndUserId(id, userId)
                .map(conv -> {
                    ChatService.AnswerResult result = chatService.ask(conv, req.question());
                    return ResponseEntity.ok(AskResponse.from(result));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ---- Request / Response DTOs -----------------------------------------

    public record CreateConversationRequest(
            UUID documentId,                                  // optional
            @Size(max = 255) String title
    ) {}

    public record AskRequest(
            @NotBlank @Size(min = 2, max = 4000) String question
    ) {}

    public record ConversationResponse(
            UUID id, UUID documentId, String title, OffsetDateTime createdAt
    ) {
        static ConversationResponse from(Conversation c) {
            return new ConversationResponse(
                    c.getId(), c.getDocumentId(), c.getTitle(), c.getCreatedAt()
            );
        }
    }

    public record MessageResponse(
            UUID id, MessageRole role, String content,
            List<UUID> citedChunkIds, Integer promptTokens,
            Integer completionTokens, String modelUsed,
            OffsetDateTime createdAt
    ) {
        static MessageResponse from(Message m) {
            return new MessageResponse(
                    m.getId(), m.getRole(), m.getContent(), m.getCitedChunkIds(),
                    m.getPromptTokens(), m.getCompletionTokens(), m.getModelUsed(),
                    m.getCreatedAt()
            );
        }
    }

    public record AskResponse(
            String answer,
            List<ChatService.Citation> citations,
            Usage usage
    ) {
        public record Usage(Integer promptTokens, Integer completionTokens, String model) {}

        static AskResponse from(ChatService.AnswerResult r) {
            return new AskResponse(
                    r.answer(),
                    r.citations(),
                    new Usage(r.promptTokens(), r.completionTokens(), r.modelUsed())
            );
        }
    }
}