package com.sonnect.api.profile;

import com.sonnect.api.auth.AuthUser;
import com.sonnect.api.common.FirestoreDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @PutMapping("/me")
    public FirestoreDataService.Profile upsertMe(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.save(user.uid(), user.email(), request.displayName(), request.photoUrl());
    }

    @GetMapping("/users/search")
    public List<FirestoreDataService.Profile> search(@AuthenticationPrincipal AuthUser user,
                                                      @RequestParam String query) {
        return profiles.search(user.uid(), query);
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 80) String displayName,
                                       @Size(max = 2048) String photoUrl) {}
}
