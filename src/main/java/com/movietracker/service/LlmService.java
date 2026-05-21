package com.movietracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// LLM integration via Groq API for recommendations and Q&A
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1";
    private static final String MODEL = "llama-3.3-70b-versatile";

    private final WebClient llmClient;
    private final String apiKey;

    public LlmService() {
        this.apiKey = System.getenv().getOrDefault("LLM_API_KEY", "");

        this.llmClient = WebClient.builder()
                .baseUrl(GROQ_API_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        if (apiKey.isEmpty()) {
            log.warn("LLM_API_KEY not set! LLM features will be disabled.");
        } else {
            log.info("LLM service configured with Groq API (model: {})", MODEL);
        }
    }

    // Get recommendations from watchlist only
    public Mono<String> getRecommendations(List<Map<String, Object>> watchlist) {
        return getRecommendations(watchlist, "");
    }

    // Get recommendations based on watchlist + user preferences
    public Mono<String> getRecommendations(List<Map<String, Object>> watchlist, String preferenceSummary) {
        if (apiKey.isEmpty()) {
            return Mono.just("LLM not configured. Please set LLM_API_KEY environment variable.");
        }

        String movieList = watchlist.stream()
                .map(m -> (String) m.get("title"))
                .collect(Collectors.joining(", "));

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Based on the user's watchlist containing these movies: ").append(movieList).append("\n\n");

        if (preferenceSummary != null && !preferenceSummary.isEmpty()) {
            promptBuilder.append("The user also has these preferences: ").append(preferenceSummary).append("\n\n");
        }

        promptBuilder.append("""
                Please recommend 5 similar movies they might enjoy. For each recommendation:
                - Movie title and year
                - Brief reason why they might like it
                
                Keep it concise and friendly.
                """);

        return chat(promptBuilder.toString());
    }

    // Find similar movies using LLM
    public Mono<String> findSimilar(String movieTitle) {
        if (apiKey.isEmpty()) {
            return Mono.just("LLM not configured. Please set LLM_API_KEY environment variable.");
        }

        String prompt = String.format("""
                Find 5 movies that are similar to "%s". For each similar movie:
                - Movie title and year
                - Brief explanation of why it's similar (tone, theme, genre, director style, etc.)
                
                Keep it concise and friendly.
                """, movieTitle);

        return chat(prompt);
    }

    public Mono<String> askAboutMovies(String question) {
        if (apiKey.isEmpty()) {
            return Mono.just("LLM not configured. Please set LLM_API_KEY environment variable.");
        }

        String prompt = String.format("""
                You are a helpful movie expert assistant. Answer the following question about movies:
                
                %s
                
                Be concise but informative.
                """, question);

        return chat(prompt);
    }

    private Mono<String> chat(String prompt) {
        Map<String, Object> request = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 500,
                "temperature", 0.7
        );

        return llmClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        return (String) message.get("content");
                    }
                    return "No response from Groq.";
                })
                .onErrorResume(e -> {
                    log.error("Groq API error: {}", e.getMessage());
                    return Mono.just("Sorry, I couldn't process your request. Please try again later.");
                });
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }
}
