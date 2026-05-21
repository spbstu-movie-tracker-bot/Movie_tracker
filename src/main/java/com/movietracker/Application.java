package com.movietracker;

import com.movietracker.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.netty.http.server.HttpServer;

// Main entry point, starts the app without Spring Boot
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

    public static void main(String[] args) {
        log.info("Starting Movie Tracker (Spring 7, non-Boot)...");

        // Create Spring context from config class
        var context = new AnnotationConfigApplicationContext(AppConfig.class);
        context.registerShutdownHook();

        // Build WebFlux HttpHandler from context
        HttpHandler httpHandler = WebHttpHandlerBuilder
                .applicationContext(context)
                .build();

        // Wrap for Reactor Netty
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);

        // Start HTTP server
        HttpServer.create()
                .port(PORT)
                .handle(adapter)
                .bindNow()
                .onDispose()
                .block();
    }
}
