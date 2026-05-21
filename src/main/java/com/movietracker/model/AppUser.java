package com.movietracker.model;

import java.time.LocalDateTime;

// User entity with role support
public class AppUser {

    private Long id;
    private Long telegramId;
    private String username;
    private String firstName;
    private String lastName;
    private Role role;
    private LocalDateTime registeredAt;
    private Boolean isActive;

    public AppUser() {
        this.role = Role.USER;
        this.registeredAt = LocalDateTime.now();
        this.isActive = true;
    }

    public AppUser(Long telegramId, String username, String firstName, String lastName) {
        this();
        this.telegramId = telegramId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public AppUser(Long id, Long telegramId, String username, String firstName, String lastName,
                   Role role, LocalDateTime registeredAt, Boolean isActive) {
        this.id = id;
        this.telegramId = telegramId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.registeredAt = registeredAt;
        this.isActive = isActive;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
