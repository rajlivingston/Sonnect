# Sonnect backend

Spring Boot REST and WebSocket backend for Sonnect. It uses Firebase Authentication for identity and Cloud Firestore for application data. No SQL database/JPA is used.

## Firestore collections

```text
users/{uid}
contacts/{uid}/items/{peerUid}
contactRequests/{requestId}
chats/{chatId}
chats/{chatId}/messages/{messageId}
```

Profile documents contain `uid`, `email`, `name`, `photoUrl`, plus lowercase `nameSearch` and `emailSearch` fields for prefix search. A user profile must be synchronized through `PUT /api/me` before other users can find it. The server Admin SDK bypasses Firestore Security Rules, so all access checks are performed by this API; keep the service account private.

## Run locally

1. Create a Cloud Firestore database in the Firebase project.
2. Create a Firebase service-account key and keep it outside the repository. Set Application Default Credentials:

   ```sh
   export GOOGLE_APPLICATION_CREDENTIALS="$HOME/secure/sonnect-service-account.json"
   export FIREBASE_PROJECT_ID="chatapp-e8ef9"
   ```

3. Deploy `firestore.indexes.json` with Firebase CLI, then run:

   ```sh
   ./mvnw spring-boot:run
   ```

The API defaults to port 8080. Android emulator clients should call `http://10.0.2.2:8080`; physical devices should use the computer's LAN IP. Set `CORS_ALLOWED_ORIGINS` to the browser origins you actually use.

## REST endpoints

Every `/api/**` endpoint requires `Authorization: Bearer <Firebase ID token>`.

| Method | Route | Purpose |
|---|---|---|
| `PUT` | `/api/me` | Sync profile; body `{ "displayName": "Name", "photoUrl": "" }` |
| `GET` | `/api/users/search?query=jo` | Prefix search by name/email |
| `GET` | `/api/contacts` | List contacts |
| `GET` | `/api/contact-requests` | Incoming requests |
| `POST` | `/api/contact-requests` | Send; body `{ "receiverUid": "..." }` |
| `POST` | `/api/contact-requests/{id}/accept` | Accept request |
| `POST` | `/api/contact-requests/{id}/reject` | Reject request |
| `GET` | `/api/chats` | List chats for current user |
| `POST` | `/api/chats/with/{uid}` | Open/create chat with accepted contact |
| `GET` | `/api/chats/{chatId}/messages` | Load latest 100 messages |

## Realtime messages

Connect to `/ws` using STOMP and pass the Firebase ID token on the `CONNECT` frame as `Authorization: Bearer <token>`. Subscribe to `/topic/chats/{chatId}` and publish JSON `{ "chatId": "...", "text": "Hello" }` to `/app/chat.send`. The server takes `senderUid` from the verified Firebase token, saves the message to Firestore, and broadcasts the persisted message. It rejects chat subscriptions by non-members.

The included simple broker is in-memory and supports a single backend instance. Use an external STOMP broker relay for multi-instance deployments. The current history endpoint returns the latest 100 messages; add cursors for long histories.

## GraalVM

The Maven project includes the Native Build Tools plugin. Native images can reduce startup time and memory, but do not guarantee higher request throughput. Build a container image with GraalVM 25 and Docker:

```sh
./mvnw -Pnative spring-boot:build-image
```

Validate Firebase Admin and its Google client dependencies with the native build for your target OS/architecture; add reachability metadata if the AOT compiler reports missing dynamic access. Keep the JVM deployment as the fallback. Firebase service-account credentials must be provided at runtime, never bundled into the native image.
