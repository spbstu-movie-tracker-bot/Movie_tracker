package com.movietracker.security;

import com.movietracker.model.Role;


////Represents an authenticated user principal.

public record AuthenticatedUser(
        Long userId,
        Long telegramId,
        Role role
) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
