package com.movietracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.EnableWebFlux;

// Main app config - enables WebFlux and component scanning
@Configuration
@EnableWebFlux
@ComponentScan(basePackages = "com.movietracker")
public class AppConfig {

}
