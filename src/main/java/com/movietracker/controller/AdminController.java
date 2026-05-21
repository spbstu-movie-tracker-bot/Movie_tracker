package com.movietracker.controller;

import com.movietracker.model.AppUser;
import com.movietracker.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

// Admin endpoints (ADMIN role only)
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public Flux<Map<String, Object>> listUsers() {
        return userService.findAllUsers()
                .map(this::userToMap);
    }

    private Map<String, Object> userToMap(AppUser user) {
        return Map.of(
                "id", user.getId(),
                "telegramId", user.getTelegramId(),
                "username", user.getUsername() != null ? user.getUsername() : "",
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName", user.getLastName() != null ? user.getLastName() : "",
                "role", user.getRole().name(),
                "registeredAt", user.getRegisteredAt().toString(),
                "isActive", user.getIsActive()
        );
    }
}
