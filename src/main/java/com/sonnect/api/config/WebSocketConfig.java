package com.sonnect.api.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.sonnect.api.auth.AuthUser;
import com.sonnect.api.chat.ChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final FirebaseApp firebaseApp;
    private final ChatService chats;
    private final List<String> allowedOrigins;

    public WebSocketConfig(FirebaseApp firebaseApp, ChatService chats,
                           @Value("${sonnect.cors.allowed-origin-patterns}") String origins) {
        this.firebaseApp = firebaseApp;
        this.chats = chats;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;

                if (accessor.getCommand() == StompCommand.CONNECT) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")) {
                        throw new MessageDeliveryException("Firebase bearer token is required");
                    }
                    try {
                        FirebaseToken token = FirebaseAuth.getInstance(firebaseApp)
                                .verifyIdToken(header.substring(7).trim());
                        AuthUser user = new AuthUser(token.getUid(), token.getEmail(), token.getName());
                        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
                    } catch (Exception invalidToken) {
                        throw new MessageDeliveryException("Firebase bearer token is required");
                    }
                } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    String destination = accessor.getDestination();
                    String uid = accessor.getUser() == null ? null : accessor.getUser().getName();
                    String prefix = "/topic/chats/";
                    if (uid == null || destination == null || !destination.startsWith(prefix)) {
                        throw new MessageDeliveryException("Subscription is not allowed");
                    }
                    chats.requireMember(uid, destination.substring(prefix.length()));
                } else if (accessor.getCommand() == StompCommand.SEND) {
                    if (!"/app/chat.send".equals(accessor.getDestination())) {
                        throw new MessageDeliveryException("Message destination is not allowed");
                    }
                }
                return message;
            }
        });
    }
}