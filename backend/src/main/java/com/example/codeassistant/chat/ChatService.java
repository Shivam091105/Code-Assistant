package com.example.codeassistant.chat;

import com.example.codeassistant.rag.RagService;
import com.example.codeassistant.rag.RagService.RagAnswer;
import com.example.codeassistant.repository.RepositoryEntity;
import com.example.codeassistant.user.User;
import com.example.codeassistant.vector.VectorSearchService.SimilarChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Coordinates chat: loads a little history, calls RagService, streams the
 * answer back to the caller token-by-token, and persists both the user's
 * question and the assistant's full answer once streaming completes.
 * Deliberately does not implement conversation summarization, vectorized
 * memory, or multi-conversation management - just the last few messages.
 */
@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final RagService ragService;
    private final int maxHistoryMessages;

    public ChatService(ChatMessageRepository chatMessageRepository, RagService ragService,
                        @Value("${app.rag.max-history-messages:6}") int maxHistoryMessages) {
        this.chatMessageRepository = chatMessageRepository;
        this.ragService = ragService;
        this.maxHistoryMessages = maxHistoryMessages;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> history(Long repositoryId) {
        return chatMessageRepository.findByRepositoryIdOrderByCreatedAtAsc(repositoryId).stream()
                .map(ChatMessageDto::from)
                .toList();
    }

    /**
     * Returns a Flux of SSE-ready text events. The first events are the
     * streamed answer tokens; a final "sources" marker event is appended so
     * the frontend can render source references once the answer is
     * complete. Persistence of both messages happens as a side effect once
     * the stream finishes.
     */
    public Flux<ChatStreamEvent> streamAnswer(User user, RepositoryEntity repository, String question) {
        chatMessageRepository.save(new ChatMessage(user, repository, ChatRole.USER, question, null));

        List<String> recentHistory = recentHistoryLines(repository.getId());
        RagAnswer ragAnswer = ragService.answerStreaming(repository.getId(), question, recentHistory);

        StringBuilder fullAnswer = new StringBuilder();

        Flux<ChatStreamEvent> tokenEvents = ragAnswer.answerStream()
                .doOnNext(fullAnswer::append)
                .map(ChatStreamEvent::token);

        Flux<ChatStreamEvent> doneEvent = Flux.defer(() -> {
            List<SimilarChunk> sources = ragAnswer.sources();
            String sourcesJoined = sources.stream()
                    .map(s -> s.filePath() + ":" + s.startLine() + "-" + s.endLine())
                    .collect(Collectors.joining("\n"));

            chatMessageRepository.save(new ChatMessage(user, repository, ChatRole.ASSISTANT, fullAnswer.toString(), sourcesJoined));

            List<String> sourceLabels = sources.stream()
                    .map(s -> s.filePath() + ":" + s.startLine() + "-" + s.endLine())
                    .toList();
            return Flux.just(ChatStreamEvent.done(sourceLabels));
        });

        return Flux.concat(tokenEvents, doneEvent);
    }

    private List<String> recentHistoryLines(Long repositoryId) {
        List<ChatMessage> recent = chatMessageRepository.findTop6ByRepositoryIdOrderByCreatedAtDesc(repositoryId);
        List<ChatMessage> ordered = new ArrayList<>(recent);
        Collections.reverse(ordered);
        int fromIndex = Math.max(0, ordered.size() - maxHistoryMessages);
        return ordered.subList(fromIndex, ordered.size()).stream()
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .toList();
    }

    public record ChatStreamEvent(String type, String token, List<String> sources) {
        static ChatStreamEvent token(String token) {
            return new ChatStreamEvent("token", token, null);
        }

        static ChatStreamEvent done(List<String> sources) {
            return new ChatStreamEvent("done", null, sources);
        }
    }
}
