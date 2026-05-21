package com.movietracker.controller;

import com.movietracker.model.AppUser;
import com.movietracker.model.Role;
import com.movietracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document;
import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;

// Tests for AdminController - generates REST Docs snippets

@ExtendWith(RestDocumentationExtension.class)
public class AdminControllerDocTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        // stub UserService that returns test data , no DB needed 
        UserService stubUserService = new UserService(null) {
            @Override
            public Flux<AppUser> findAllUsers() {
                AppUser user1 = new AppUser(1L, 123456789L, "msalem",
                        "Mohamed", "Salem", Role.ADMIN,
                        LocalDateTime.of(2026, 5, 1, 10, 0), true);
                AppUser user2 = new AppUser(2L, 987654321L, "mikhail",
                        "Mikhail", "Alekhin", Role.USER,
                        LocalDateTime.of(2026, 5, 2, 12, 30), true);
                return Flux.just(user1, user2);
            }
        };

        this.webTestClient = WebTestClient
                .bindToController(new AdminController(stubUserService))
                .configureClient()
                .filter(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void listUsersReturnsAllUsers() {
        webTestClient.get().uri("/admin/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                .consumeWith(document("admin-users",
                        responseFields(
                                fieldWithPath("[].id").description("User ID in database"),
                                fieldWithPath("[].telegramId").description("Telegram user ID"),
                                fieldWithPath("[].username").description("Telegram username"),
                                fieldWithPath("[].firstName").description("First name"),
                                fieldWithPath("[].lastName").description("Last name"),
                                fieldWithPath("[].role").description("User role (USER or ADMIN)"),
                                fieldWithPath("[].registeredAt").description("Registration timestamp"),
                                fieldWithPath("[].isActive").description("Whether user is active")
                        )
                ));
    }

    @Test
    void listUsersContainsAdminRole() {
        webTestClient.get().uri("/admin/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].role").isEqualTo("ADMIN")
                .jsonPath("$[1].role").isEqualTo("USER")
                .consumeWith(document("admin-users-roles"));
    }
}
