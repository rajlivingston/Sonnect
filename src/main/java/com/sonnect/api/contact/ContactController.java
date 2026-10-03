package com.sonnect.api.contact;

import com.sonnect.api.auth.AuthUser;
import com.sonnect.api.common.FirestoreDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ContactController {
    private final ContactService contacts;

    public ContactController(ContactService contacts) { this.contacts = contacts; }

    @GetMapping("/contacts")
    public List<FirestoreDataService.Profile> contacts(@AuthenticationPrincipal AuthUser user) {
        return contacts.contactsFor(user.uid());
    }

    @GetMapping("/contact-requests")
    public List<FirestoreDataService.ContactRequestView> requests(@AuthenticationPrincipal AuthUser user) {
        return contacts.requestsFor(user.uid());
    }

    @PostMapping("/contact-requests")
    public FirestoreDataService.ContactRequestView send(@AuthenticationPrincipal AuthUser user,
                                                        @Valid @RequestBody SendRequest request) {
        return contacts.send(user.uid(), request.receiverUid());
    }

    @PostMapping("/contact-requests/{requestId}/accept")
    public void accept(@AuthenticationPrincipal AuthUser user, @PathVariable String requestId) {
        contacts.respond(user.uid(), requestId, true);
    }

    @PostMapping("/contact-requests/{requestId}/reject")
    public void reject(@AuthenticationPrincipal AuthUser user, @PathVariable String requestId) {
        contacts.respond(user.uid(), requestId, false);
    }

    public record SendRequest(@NotBlank @Size(max = 128) String receiverUid) {}
}
