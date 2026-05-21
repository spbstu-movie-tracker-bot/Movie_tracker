package com.movietracker.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;



///Public healthcheck endpoint (no auth required) 

@RestController
public class HealthcheckController {

    private static final List<String> AUTHORS = List.of(
            "Abbad Mohamed Salem",
            "Mikhail Igorevich Alekhin",
            "Olga Alekseyevna Kozlovskaya"
    );

    @GetMapping("/healthcheck")
    public Mono<Map<String, Object>> healthcheck() {
        return Mono.just(Map.of(
                "status", "UP",
                "server", "Movie Tracker",
                "authors", AUTHORS
        ));
    }
}
