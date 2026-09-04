package com.example.codeassistant.chat;

import com.example.codeassistant.repository.RepositoryEntity;
import com.example.codeassistant.repository.RepositoryService;
import com.example.codeassistant.security.CurrentUser;
import com.example.codeassistant.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/repositories/{githubRepositoryId}")
public class ChatController {

    private final ChatService chatService;
    private final RepositoryService repositoryService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, RepositoryService repositoryService,
                           CurrentUser currentUser, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.repositoryService = repositoryService;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/messages")
    public List<ChatMessageDto> messages(@PathVariable Long githubRepositoryId, Authentication authentication) {
        RepositoryEntity repository = resolveOwnedRepository(githubRepositoryId, authentication);
        return chatService.history(repository.getId());
    }

    /**
     * Streams the assistant's answer as Server-Sent Events. Each event's
     * data is a small JSON payload: {"type":"token","token":"..."} while the
     * answer is generating, followed by a single
     * {"type":"done","sources":[...]} event once the LLM finishes.
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@PathVariable Long githubRepositoryId,
                              @Valid @RequestBody ChatRequest request,
                              Authentication authentication) {
        User user = currentUser.resolve(authentication);
        RepositoryEntity repository = repositoryService.findOwnedOrCreateFromGitHub(authentication, user, githubRepositoryId);

        return chatService.streamAnswer(user, repository, request.question())
                .map(this::toSseData);
    }

    private RepositoryEntity resolveOwnedRepository(Long githubRepositoryId, Authentication authentication) {
        User user = currentUser.resolve(authentication);
        return repositoryService.findOwnedOrCreateFromGitHub(authentication, user, githubRepositoryId);
    }

    private String toSseData(ChatService.ChatStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\"}";
        }
    }
}
