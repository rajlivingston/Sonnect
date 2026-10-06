package com.sonnect.api.auth;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class FirebaseTokenFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseTokenFilter.class);
    private final FirebaseApp firebaseApp;

    public FirebaseTokenFilter(FirebaseApp firebaseApp) {
        this.firebaseApp = firebaseApp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean contactRequestApi = request.getRequestURI().startsWith("/api/contact-requests");
        boolean hasBearerToken = authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7);

        if (!hasBearerToken) {
            if (contactRequestApi) {
                logger.warn("Contact request API received {} {} without a Firebase Bearer token",
                        request.getMethod(), request.getRequestURI());
            }
            doFilterAndLogStatus(request, response, filterChain, contactRequestApi);
            return;
        }

        final FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance(firebaseApp)
                    .verifyIdToken(authorization.substring(7).trim());
        } catch (Exception invalidToken) {
            SecurityContextHolder.clearContext();
            if (contactRequestApi) {
                logger.warn("Firebase token rejected for contact request API {} {} ({})",
                        request.getMethod(), request.getRequestURI(),
                        invalidToken.getClass().getSimpleName());
            }
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Firebase ID token");
            if (contactRequestApi) {
                logger.info("Contact request API {} {} completed with HTTP {}",
                        request.getMethod(), request.getRequestURI(), response.getStatus());
            }
            return;
        }

        AuthUser principal = new AuthUser(decoded.getUid(), decoded.getEmail(), decoded.getName());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        if (contactRequestApi) {
            logger.info("Firebase token accepted for contact request API {} {}",
                    request.getMethod(), request.getRequestURI());
        }
        doFilterAndLogStatus(request, response, filterChain, contactRequestApi);
    }

    private void doFilterAndLogStatus(HttpServletRequest request, HttpServletResponse response,
                                      FilterChain filterChain, boolean contactRequestApi)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (contactRequestApi) {
                logger.info("Contact request API {} {} completed with HTTP {}",
                        request.getMethod(), request.getRequestURI(), response.getStatus());
            }
        }
    }
}
