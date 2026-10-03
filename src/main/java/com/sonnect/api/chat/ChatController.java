package com.sonnect.api.chat;

import com.sonnect.api.auth.AuthUser;
import com.sonnect.api.common.FirestoreDataService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chats;

    public ChatController(ChatService chats) { this.chats = chats; }

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
                                                            @PathVariable String chatId) {
        return chats.messagesFor(user.uid(), chatId);
    }
}
