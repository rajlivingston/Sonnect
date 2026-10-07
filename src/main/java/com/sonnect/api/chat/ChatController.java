package com.sonnect.api.chat;

import com.sonnect.api.auth.AuthUser;
import com.sonnect.api.common.FirestoreDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chats;
    private final SimpMessagingTemplate messaging;

    public ChatController(ChatService chats, SimpMessagingTemplate messaging) {
        this.chats = chats;
        this.messaging = messaging;
    }

    @GetMapping
    public List<FirestoreDataService.ChatView> list(@AuthenticationPrincipal AuthUser user) {
        return chats.chatsFor(user.uid());
    }

    @PostMapping("/with/{otherUid}")
    public FirestoreDataService.ChatView open(@AuthenticationPrincipal AuthUser user,
                                               @PathVariable String otherUid) {
        return chats.open(user.uid(), otherUid);
    }

    @GetMapping("/{chatId}/messages")
    public List<FirestoreDataService.MessageView> messages(@AuthenticationPrincipal AuthUser user,
                                                            @PathVariable String chatId,
                                                            @RequestParam(required = false) Instant after) {
        return chats.messagesFor(user.uid(), chatId, after);
    }

    @PostMapping("/{chatId}/messages")
    public FirestoreDataService.MessageView send(@AuthenticationPrincipal AuthUser user,
                                                 @PathVariable String chatId,
                                                 @Valid @RequestBody SendMessage request) {
        FirestoreDataService.MessageView saved = chats.send(user.uid(), chatId, request.text());
        messaging.convertAndSend("/topic/chats/" + saved.chatId(), saved);
        return saved;
    }

    public record SendMessage(@NotBlank @Size(max = 4000) String text) {}
}
