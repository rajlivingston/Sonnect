package com.sonnect.api.common;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class FirestoreDataService {
    private static final String USERS = "users";
    private static final String CONTACT_REQUESTS = "contactRequests";
    private static final String CONTACTS = "contacts";
    private static final String CHATS = "chats";

    private final Firestore firestore;

    public FirestoreDataService(Firestore firestore) {
        this.firestore = firestore;
    }

    public Profile upsertProfile(String uid, String email, String displayName, String photoUrl) {
        String safeName = displayName == null || displayName.isBlank() ? "Sonnect user" : displayName.trim();
        String safeEmail = email == null ? "" : email.trim().toLowerCase();
        String safePhoto = photoUrl == null ? "" : photoUrl.trim();
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("email", safeEmail);
        data.put("emailSearch", safeEmail);
        data.put("name", safeName);
        data.put("nameSearch", safeName.toLowerCase());
        data.put("photoUrl", safePhoto);
        data.put("updatedAt", Timestamp.now());
        await(users().document(uid).set(data, SetOptions.merge()));
        return new Profile(uid, uid, safeEmail, safeName, safePhoto);
    }

    public List<Profile> searchProfiles(String callerUid, String query) {
        String prefix = query == null ? "" : query.trim().toLowerCase();
        if (prefix.length() < 2) return List.of();

        Map<String, Profile> matches = new HashMap<>();
        Query nameQuery = users().whereGreaterThanOrEqualTo("nameSearch", prefix)
                .whereLessThanOrEqualTo("nameSearch", prefix + "\uf8ff")
                .orderBy("nameSearch").limit(20);
        Query emailQuery = users().whereGreaterThanOrEqualTo("emailSearch", prefix)
                .whereLessThanOrEqualTo("emailSearch", prefix + "\uf8ff")
                .orderBy("emailSearch").limit(20);

        addProfiles(await(nameQuery.get()), callerUid, matches);
        addProfiles(await(emailQuery.get()), callerUid, matches);
        return matches.values().stream()
                .sorted(Comparator.comparing(Profile::name, String.CASE_INSENSITIVE_ORDER))
                .limit(20).toList();
    }

    public List<Profile> contactsFor(String uid) {
        QuerySnapshot snapshot = await(firestore.collection(CONTACTS).document(uid)
                .collection("items").get());
        List<Profile> result = new ArrayList<>();
        for (QueryDocumentSnapshot contact : snapshot.getDocuments()) {
            DocumentSnapshot user = await(users().document(contact.getId()).get());
            if (user.exists()) result.add(profile(user));
        }
        return result;
    }

    public List<ContactRequestView> requestsFor(String uid) {
        QuerySnapshot snapshot = await(firestore.collection(CONTACT_REQUESTS)
                .whereEqualTo("receiverUid", uid)
                .whereEqualTo("status", "PENDING").get());
        List<ContactRequestView> result = new ArrayList<>();
        for (QueryDocumentSnapshot request : snapshot.getDocuments()) {
            DocumentSnapshot sender = await(users().document(request.getString("senderUid")).get());
            if (!sender.exists()) continue;
            result.add(new ContactRequestView(request.getId(), request.getString("senderUid"),
                    sender.getString("name"), sender.getString("email"), instant(request.getTimestamp("createdAt"))));
        }
        result.sort(Comparator.comparing(ContactRequestView::createdAt).reversed());
        return result;
    }

    public ContactRequestView sendContactRequest(String senderUid, String receiverUid) {
        if (senderUid.equals(receiverUid)) throw badRequest("You cannot add yourself");
        if (!await(users().document(receiverUid).get()).exists()) throw notFound("User not found");
        if (await(contactRef(senderUid, receiverUid).get()).exists())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already a contact");

        String id = requestId(senderUid, receiverUid);
        String reverseId = requestId(receiverUid, senderUid);
        DocumentReference requestRef = firestore.collection(CONTACT_REQUESTS).document(id);
        DocumentSnapshot existing = await(requestRef.get());
        DocumentSnapshot reverse = await(firestore.collection(CONTACT_REQUESTS).document(reverseId).get());
        if (pending(existing) || pending(reverse))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending request already exists");

        Profile sender = profile(await(users().document(senderUid).get()));
        Map<String, Object> data = new HashMap<>();
        data.put("senderUid", senderUid);
        data.put("receiverUid", receiverUid);
        data.put("status", "PENDING");
        data.put("createdAt", Timestamp.now());
        await(requestRef.set(data));
        return new ContactRequestView(id, senderUid, sender.name(), sender.email(), Instant.now());
    }

    public void respondToContactRequest(String receiverUid, String requestId, boolean accept) {
        DocumentReference requestRef = firestore.collection(CONTACT_REQUESTS).document(requestId);
        await(firestore.runTransaction(transaction -> {
            DocumentSnapshot request = transaction.get(requestRef).get();
            if (!request.exists()) throw notFound("Request not found");
            if (!receiverUid.equals(request.getString("receiverUid")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This request is not addressed to you");
            if (!"PENDING".equals(request.getString("status")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request has already been handled");

            String senderUid = request.getString("senderUid");
            transaction.update(requestRef, "status", accept ? "ACCEPTED" : "REJECTED");
            if (accept) {
                Timestamp now = Timestamp.now();
                transaction.set(contactRef(receiverUid, senderUid), Map.of("uid", senderUid, "createdAt", now));
                transaction.set(contactRef(senderUid, receiverUid), Map.of("uid", receiverUid, "createdAt", now));
            }
            return null;
        }));
    }

    public boolean areContacts(String firstUid, String secondUid) {
        return await(contactRef(firstUid, secondUid).get()).exists()
                && await(contactRef(secondUid, firstUid).get()).exists();
    }

    public ChatView openChat(String uid, String otherUid) {
        if (uid.equals(otherUid)) throw badRequest("Cannot chat with yourself");
        if (!areContacts(uid, otherUid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Add this user as a contact before chatting");
        List<String> members = new ArrayList<>(List.of(uid, otherUid));
        members.sort(String::compareTo);
        String chatId = members.getFirst() + "_" + members.getLast();
        DocumentReference chatRef = firestore.collection(CHATS).document(chatId);
        DocumentSnapshot existing = await(chatRef.get());
        if (!existing.exists()) {
            Map<String, Object> data = new HashMap<>();
            data.put("members", members);
            data.put("lastMessage", "");
            data.put("createdAt", Timestamp.now());
            data.put("updatedAt", Timestamp.now());
            await(chatRef.set(data, SetOptions.merge()));
            existing = await(chatRef.get());
        }
        return chat(chatId, existing);
    }

    public List<ChatView> chatsFor(String uid) {
        QuerySnapshot snapshot = await(firestore.collection(CHATS)
                .whereArrayContains("members", uid).get());
        return snapshot.getDocuments().stream()
                .map(doc -> chat(doc.getId(), doc))
                .sorted(Comparator.comparing(ChatView::updatedAt).reversed())
                .toList();
    }

    public List<MessageView> messagesFor(String uid, String chatId) {
        requireMember(uid, chatId);
        QuerySnapshot snapshot = await(firestore.collection(CHATS).document(chatId)
                .collection("messages").orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100).get());
        return snapshot.getDocuments().stream()
                .map(doc -> message(chatId, doc))
                .sorted(Comparator.comparing(MessageView::createdAt))
                .toList();
    }

    public MessageView sendMessage(String uid, String chatId, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty() || text.length() > 4000)
            throw badRequest("Message must contain 1 to 4000 characters");

        DocumentReference chatRef = firestore.collection(CHATS).document(chatId);
        DocumentReference messageRef = chatRef.collection("messages").document();
        Timestamp now = Timestamp.now();
        await(firestore.runTransaction(transaction -> {
            DocumentSnapshot chat = transaction.get(chatRef).get();
            validateMember(uid, chatId, chat);
            Map<String, Object> message = new HashMap<>();
            message.put("senderUid", uid);
            message.put("text", text);
            message.put("type", "TEXT");
            message.put("status", "SENT");
            message.put("createdAt", now);
            transaction.create(messageRef, message);
            transaction.update(chatRef, Map.of("lastMessage", text, "updatedAt", now));
            return null;
        }));
        return new MessageView(messageRef.getId(), chatId, uid, text, "TEXT", "SENT", now.toDate().toInstant());
    }

    public void requireMember(String uid, String chatId) {
        DocumentSnapshot chat = await(firestore.collection(CHATS).document(chatId).get());
        validateMember(uid, chatId, chat);
    }

    private void validateMember(String uid, String chatId, DocumentSnapshot chat) {
        if (!chat.exists()) throw notFound("Chat not found");
        List<?> members = (List<?>) chat.get("members");
        if (members == null || !members.contains(uid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this chat");
    }

    private void addProfiles(QuerySnapshot query, String callerUid, Map<String, Profile> results) {
        for (QueryDocumentSnapshot doc : query.getDocuments()) {
            if (!doc.getId().equals(callerUid)) results.put(doc.getId(), profile(doc));
        }
    }

    private Profile profile(DocumentSnapshot doc) {
        return new Profile(doc.getId(), doc.getId(), value(doc.getString("email")),
                value(doc.getString("name")), value(doc.getString("photoUrl")));
    }

    private ChatView chat(String id, DocumentSnapshot doc) {
        List<?> rawMembers = (List<?>) doc.get("members");
        List<String> members = rawMembers == null ? List.of() : rawMembers.stream().map(String::valueOf).toList();
        return new ChatView(id, members, value(doc.getString("lastMessage")),
                instant(doc.getTimestamp("createdAt")), instant(doc.getTimestamp("updatedAt")));
    }

    private MessageView message(String chatId, DocumentSnapshot doc) {
        return new MessageView(doc.getId(), chatId, value(doc.getString("senderUid")),
                value(doc.getString("text")), value(doc.getString("type")),
                value(doc.getString("status")), instant(doc.getTimestamp("createdAt")));
    }

    private DocumentReference contactRef(String uid, String peerUid) {
        return firestore.collection(CONTACTS).document(uid).collection("items").document(peerUid);
    }

    private com.google.cloud.firestore.CollectionReference users() {
        return firestore.collection(USERS);
    }

    private boolean pending(DocumentSnapshot snapshot) {
        return snapshot.exists() && "PENDING".equals(snapshot.getString("status"));
    }

    private String requestId(String senderUid, String receiverUid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((senderUid + "\u0000" + receiverUid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toDate().toInstant();
    }

    private String value(String value) { return value == null ? "" : value; }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Interrupted while accessing Firestore", interrupted);
        } catch (ExecutionException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Firestore request failed", failure.getCause());
        }
    }

    public record Profile(String id, String uid, String email, String name, String photoUrl) {}
    public record ContactRequestView(String id, String senderId, String name, String email, Instant createdAt) {}
    public record ChatView(String id, List<String> members, String lastMessage, Instant createdAt, Instant updatedAt) {}
    public record MessageView(String id, String chatId, String senderId, String text,
                              String type, String status, Instant createdAt) {}
}
