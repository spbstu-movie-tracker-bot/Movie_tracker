package com.movietracker.service;

import com.movietracker.repository.PreferencesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Manages user preferences (genres, actors, directors)
@Service
public class PreferencesService {

    private static final Logger log = LoggerFactory.getLogger(PreferencesService.class);
    private final PreferencesRepository preferencesRepository;

    public PreferencesService(PreferencesRepository preferencesRepository) {
        this.preferencesRepository = preferencesRepository;
    }

    // Set preferences from comma-separated values, replaces old ones
    public Mono<List<String>> setPreferences(Long userId, String prefType, String valuesStr) {
        List<String> values = Arrays.stream(valuesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (values.isEmpty()) {
            return Mono.just(List.of());
        }

        // Delete existing preferences of this type, then add new ones
        return preferencesRepository.deletePreferencesByType(userId, prefType)
                .thenMany(Flux.fromIterable(values)
                        .concatMap(value -> preferencesRepository.addPreference(userId, prefType, value.toLowerCase())))
                .then(Mono.just(values));
    }

    // Get all preferences grouped by type
    public Mono<Map<String, List<String>>> getAllPreferences(Long userId) {
        return preferencesRepository.getAllPreferences(userId)
                .collectList()
                .map(prefs -> prefs.stream()
                        .collect(Collectors.groupingBy(
                                p -> p.get("type"),
                                Collectors.mapping(p -> p.get("value"), Collectors.toList())
                        )));
    }

    // Get preferences by type
    public Mono<List<String>> getPreferencesByType(Long userId, String prefType) {
        return preferencesRepository.getPreferences(userId, prefType)
                .map(p -> p.get("value"))
                .collectList();
    }

    // Clear all user preferences
    public Mono<Void> clearAllPreferences(Long userId) {
        return preferencesRepository.deleteAllPreferences(userId).then();
    }

    // Clear preferences of a specific type
    public Mono<Void> clearPreferencesByType(Long userId, String prefType) {
        return preferencesRepository.deletePreferencesByType(userId, prefType).then();
    }

    // Format preferences as readable string
    public Mono<String> formatPreferences(Long userId) {
        return getAllPreferences(userId)
                .map(prefs -> {
                    if (prefs.isEmpty()) {
                        return "No preferences set yet.";
                    }

                    StringBuilder sb = new StringBuilder();
                    if (prefs.containsKey("genre")) {
                        sb.append("🎭 <b>Genres:</b> ").append(String.join(", ", prefs.get("genre"))).append("\n");
                    }
                    if (prefs.containsKey("actor")) {
                        sb.append("🎭 <b>Actors:</b> ").append(String.join(", ", prefs.get("actor"))).append("\n");
                    }
                    if (prefs.containsKey("director")) {
                        sb.append("🎬 <b>Directors:</b> ").append(String.join(", ", prefs.get("director"))).append("\n");
                    }
                    return sb.toString().trim();
                });
    }

    // Build preference summary for LLM prompt
    public Mono<String> buildPreferenceSummary(Long userId) {
        return getAllPreferences(userId)
                .map(prefs -> {
                    if (prefs.isEmpty()) return "";

                    StringBuilder sb = new StringBuilder();
                    if (prefs.containsKey("genre")) {
                        sb.append("Favorite genres: ").append(String.join(", ", prefs.get("genre"))).append(". ");
                    }
                    if (prefs.containsKey("actor")) {
                        sb.append("Favorite actors: ").append(String.join(", ", prefs.get("actor"))).append(". ");
                    }
                    if (prefs.containsKey("director")) {
                        sb.append("Favorite directors: ").append(String.join(", ", prefs.get("director"))).append(". ");
                    }
                    return sb.toString().trim();
                });
    }
}
