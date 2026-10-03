package com.sonnect.api.profile;

import com.sonnect.api.common.FirestoreDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileService {
    private final FirestoreDataService firestore;

    public ProfileService(FirestoreDataService firestore) { this.firestore = firestore; }

    public FirestoreDataService.Profile save(String uid, String email, String name, String photoUrl) {
        return firestore.upsertProfile(uid, email, name, photoUrl);
    }

    public List<FirestoreDataService.Profile> search(String uid, String query) {
        return firestore.searchProfiles(uid, query);
    }
}
