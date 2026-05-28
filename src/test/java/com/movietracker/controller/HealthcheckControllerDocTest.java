package com.movietracker.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document;
import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;

// Tests for HealthcheckController - generates REST Docs snippets

@ExtendWith(RestDocumentationExtension.class)
public class HealthcheckControllerDocTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        // bind directly to the controller, no full app context needed
        this.webTestClient = WebTestClient
                .bindToController(new HealthcheckController())
                .configureClient()
                .filter(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    void healthcheckReturnsStatusAndAuthors() {
        webTestClient.get().uri("/healthcheck")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.server").isEqualTo("Movie Tracker")
                .jsonPath("$.authors").isArray()
                .consumeWith(document("healthcheck",
                        responseFields(
                                fieldWithPath("status").description("Server status"),
                                fieldWithPath("server").description("Application name"),
                                fieldWithPath("authors").description("List of project authors")
                        )
                ));
    }

    @Test
    void healthcheckResponseFormat() {
        webTestClient.get().uri("/healthcheck")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authors.length()").isEqualTo(3)
                .consumeWith(document("healthcheck-authors"));
    }
}
