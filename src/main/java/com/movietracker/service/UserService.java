package com.movietracker.service;

import com.movietracker.model.AppUser;
import com.movietracker.model.Role;
import com.movietracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// User management service
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<AppUser> registerOrGetUser(Long telegramId, String username,
                                            String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId)
                .doOnNext(user -> log.debug("User already registered: {}", user.getId()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Registering new user with telegramId={}", telegramId);
                    AppUser newUser = new AppUser(telegramId, username, firstName, lastName);
                    return userRepository.save(newUser);
                }));
    }

    public Mono<AppUser> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public Flux<AppUser> findAllUsers() {
        return userRepository.findAll();
    }

    public Mono<Integer> promoteToAdmin(Long userId) {
        return userRepository.updateRole(userId, Role.ADMIN);
    }
}
