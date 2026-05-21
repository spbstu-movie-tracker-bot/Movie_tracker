package com.movietracker.security;

import com.movietracker.model.AppUser;
import com.movietracker.model.Role;
import com.movietracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

// Auth manager - validates Telegram user via header or Bearer token
@Component
public class TelegramAuthManager implements ReactiveAuthenticationManager, ServerAuthenticationConverter {

    private static final Logger log = LoggerFactory.getLogger(TelegramAuthManager.class);
    private static final String TELEGRAM_USER_HEADER = "X-Telegram-User-Id";

    private final UserRepository userRepository;

    public TelegramAuthManager(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String telegramUserId = exchange.getRequest().getHeaders().getFirst(TELEGRAM_USER_HEADER);
        
        if (telegramUserId != null) {
            return Mono.just(new UsernamePasswordAuthenticationToken(telegramUserId, null));
        }

        // Check for Bearer token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return Mono.just(new UsernamePasswordAuthenticationToken(token, null));
        }

        return Mono.empty();
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String principal = (String) authentication.getPrincipal();
        
        try {
            Long telegramId = Long.parseLong(principal);
            return userRepository.findByTelegramId(telegramId)
                    .map(this::createAuthentication)
                    .doOnNext(auth -> log.debug("Authenticated user: {}", telegramId))
                    .switchIfEmpty(Mono.error(new RuntimeException("User not found")));
        } catch (NumberFormatException e) {
            // Token-based auth - validate token
            return validateToken(principal);
        }
    }

    private Mono<Authentication> validateToken(String token) {
        // Simple token format: "telegramId:secretHash"
        // In production, use proper JWT or similar
        String[] parts = token.split(":");
        if (parts.length != 2) {
            return Mono.error(new RuntimeException("Invalid token format"));
        }

        try {
            Long telegramId = Long.parseLong(parts[0]);
            return userRepository.findByTelegramId(telegramId)
                    .map(this::createAuthentication)
                    .switchIfEmpty(Mono.error(new RuntimeException("User not found")));
        } catch (NumberFormatException e) {
            return Mono.error(new RuntimeException("Invalid token"));
        }
    }

    private Authentication createAuthentication(AppUser user) {
        List<SimpleGrantedAuthority> authorities = user.getRole() == Role.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(user.getId(), user.getTelegramId(), user.getRole()),
                null,
                authorities
        );
    }
}
