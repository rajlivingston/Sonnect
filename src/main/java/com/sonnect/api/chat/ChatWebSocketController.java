package com.sonnect.api.chat;

import com.sonnect.api.common.FirestoreDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {
    private final ChatService chats;
    private final SimpMessagingTemplate messaging;

    public ChatWebSocketController(ChatService chats, SimpMessagingTemplate messaging) {
        this.chats = chats;
        this.messaging = messaging;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid SendMessage request, Principal principal) {
        if (principal == null) throw new IllegalStateException("WebSocket user is not authenticated");
        FirestoreDataService.MessageView saved = chats.send(
                principal.getName(), request.chatId(), request.text());
        messaging.convertAndSend("/topic/chats/" + saved.chatId(), saved);
    }

    public record SendMessage(@NotBlank @Size(max = 160) String chatId,
                              @NotBlank @Size(max = 4000) String text) {}
}
