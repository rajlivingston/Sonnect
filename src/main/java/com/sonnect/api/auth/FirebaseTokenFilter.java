package com.sonnect.api.auth;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class FirebaseTokenFilter extends OncePerRequestFilter {
    private final FirebaseApp firebaseApp;

    public FirebaseTokenFilter(FirebaseApp firebaseApp) {
        this.firebaseApp = firebaseApp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance(firebaseApp)
                    .verifyIdToken(authorization.substring(7).trim());
        } catch (Exception invalidToken) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Firebase ID token");
            return;
        }

        AuthUser principal = new AuthUser(decoded.getUid(), decoded.getEmail(), decoded.getName());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
