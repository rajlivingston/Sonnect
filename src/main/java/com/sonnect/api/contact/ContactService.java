package com.sonnect.api.contact;

import com.sonnect.api.common.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactService {
    private final FirestoreDataService firestore;

    public ContactService(FirestoreDataService firestore) { this.firestore = firestore; }

    public List<FirestoreDataService.Profile> contactsFor(String uid) {
        return firestore.contactsFor(uid);
    }

    public List<FirestoreDataService.ContactRequestView> requestsFor(String uid) {
        return firestore.requestsFor(uid);
    }

    public FirestoreDataService.ContactRequestView send(String senderUid, String receiverUid) {
        return firestore.sendContactRequest(senderUid, receiverUid);
    }

    public void respond(String receiverUid, String requestId, boolean accept) {
        firestore.respondToContactRequest(receiverUid, requestId, accept);
    }
}
