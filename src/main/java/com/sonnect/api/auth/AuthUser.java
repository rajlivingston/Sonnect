package com.sonnect.api.auth;

import java.security.Principal;

public record AuthUser(String uid, String email, String displayName) implements Principal {
    @Override
    public String getName() {
        return uid;
    }
}
