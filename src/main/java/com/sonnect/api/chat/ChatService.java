package com.sonnect.api.chat;

import com.sonnect.api.common.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {
    private final FirestoreDataService firestore;

    public ChatService(FirestoreDataService firestore) { this.firestore = firestore; }

    public FirestoreDataService.ChatView open(String uid, String otherUid) {
        return firestore.openChat(uid, otherUid);
    }

    public List<FirestoreDataService.ChatView> chatsFor(String uid) {
        return firestore.chatsFor(uid);
    }

    public List<FirestoreDataService.MessageView> messagesFor(String uid, String chatId) {
        return firestore.messagesFor(uid, chatId);
    }

    public FirestoreDataService.MessageView send(String uid, String chatId, String text) {
        return firestore.sendMessage(uid, chatId, text);
    }

    public void requireMember(String uid, String chatId) {
        firestore.requireMember(uid, chatId);
    }
}
