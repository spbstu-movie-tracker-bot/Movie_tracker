package com.movietracker.service;

import com.movietracker.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Movie operations + watchlist management
@Service
public class MovieService {

    private static final Logger log = LoggerFactory.getLogger(MovieService.class);
    private final WebClient tmdbClient;
    private final String apiKey;
    private final WatchlistRepository watchlistRepository;

    // TMDb genre IDs mapping
    private static final Map<String, Integer> GENRE_MAP = Map.ofEntries(
            Map.entry("action", 28), Map.entry("adventure", 12),
            Map.entry("animation", 16), Map.entry("comedy", 35),
            Map.entry("crime", 80), Map.entry("documentary", 99),
            Map.entry("drama", 18), Map.entry("family", 10751),
            Map.entry("fantasy", 14), Map.entry("history", 36),
            Map.entry("horror", 27), Map.entry("music", 10402),
            Map.entry("mystery", 9648), Map.entry("romance", 10749),
            Map.entry("science fiction", 878), Map.entry("sci-fi", 878),
            Map.entry("thriller", 53), Map.entry("war", 10752),
            Map.entry("western", 37)
    );

    public MovieService(WatchlistRepository watchlistRepository) {
        this.watchlistRepository = watchlistRepository;
        this.apiKey = System.getenv().getOrDefault("TMDB_API_KEY", "");
        this.tmdbClient = WebClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Accept", "application/json")
                .build();

        if (apiKey.isEmpty()) {
            log.warn("TMDB_API_KEY not set!");
        } else {
            log.info("TMDb service configured");
        }
    }

    public Flux<Map<String, Object>> searchMovies(String query) {
        if (apiKey.isEmpty()) {
            return Flux.empty();
        }

        return tmdbClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/movie")
                        .queryParam("query", query)
                        .queryParam("language", "en-US")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null) return Flux.empty();

                    return Flux.fromIterable(results)
                            .map(this::mapTmdbMovie);
                })
                .doOnError(e -> log.error("TMDb search failed", e));
    }

    public Mono<Map<String, Object>> getMovieDetails(Integer tmdbId) {
        if (apiKey.isEmpty()) {
            return Mono.empty();
        }

        return tmdbClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/{id}")
                        .build(tmdbId))
                .retrieve()
                .bodyToMono(Map.class)
                .map(movie -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("tmdbId", movie.get("id"));
                    result.put("title", movie.getOrDefault("title", "Unknown"));
                    result.put("releaseYear", extractYear((String) movie.get("release_date")));
                    result.put("releaseDate", movie.getOrDefault("release_date", "N/A"));
                    result.put("rating", movie.getOrDefault("vote_average", 0.0));
                    result.put("overview", movie.getOrDefault("overview", ""));
                    result.put("runtime", movie.getOrDefault("runtime", 0));
                    result.put("posterPath", movie.getOrDefault("poster_path", ""));
                    return result;
                });
    }

    // Discover movies with optional genre filter
    public Flux<Map<String, Object>> discoverMovies(List<String> genres) {
        if (apiKey.isEmpty()) {
            return Flux.empty();
        }

        // Convert genre names to TMDb genre IDs
        String genreIds = genres.stream()
                .map(g -> GENRE_MAP.get(g.toLowerCase()))
                .filter(id -> id != null)
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        int randomPage = ThreadLocalRandom.current().nextInt(1, 10);

        return tmdbClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/discover/movie")
                            .queryParam("language", "en-US")
                            .queryParam("sort_by", "popularity.desc")
                            .queryParam("vote_count.gte", 100)
                            .queryParam("page", randomPage);
                    if (!genreIds.isEmpty()) {
                        builder = builder.queryParam("with_genres", genreIds);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(Map.class)
                .flatMapMany(response -> {
                    List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                    if (results == null) return Flux.empty();
                    return Flux.fromIterable(results).map(this::mapTmdbMovie);
                })
                .doOnError(e -> log.error("TMDb discover failed", e));
    }

    // Get a random movie, optionally by preferred genres
    public Mono<Map<String, Object>> getRandomMovie(List<String> preferredGenres) {
        return discoverMovies(preferredGenres != null ? preferredGenres : List.of())
                .collectList()
                .flatMap(movies -> {
                    if (movies.isEmpty()) {
                        return Mono.empty();
                    }
                    int idx = ThreadLocalRandom.current().nextInt(movies.size());
                    return Mono.just(movies.get(idx));
                });
    }

    public Mono<Void> addToWatchlist(Long userId, Integer tmdbId) {
        log.info("Adding movie tmdbId={} to watchlist for user {}", tmdbId, userId);

        // First fetch movie details from TMDb, save to DB, then add to watchlist
        return getMovieDetails(tmdbId)
                .switchIfEmpty(Mono.just(Map.<String, Object>of(
                        "tmdbId", tmdbId,
                        "title", "Unknown Movie"
                )))
                .flatMap(movieData -> watchlistRepository.findOrCreateMovie(movieData))
                .flatMap(movieId -> watchlistRepository.addToWatchlist(userId, movieId))
                .doOnSuccess(v -> log.info("Movie tmdbId={} added to watchlist for user {}", tmdbId, userId));
    }

    public Mono<Void> removeFromWatchlist(Long userId, Long watchlistId) {
        log.info("Removing watchlist entry {} for user {}", watchlistId, userId);
        return watchlistRepository.removeFromWatchlist(userId, watchlistId);
    }

    public Mono<Void> markAsWatched(Long userId, Long watchlistId) {
        log.info("Marking watchlist entry {} as watched for user {}", watchlistId, userId);
        return watchlistRepository.markAsWatched(userId, watchlistId);
    }

    public Flux<Map<String, Object>> getWatchlist(Long userId) {
        log.debug("Getting watchlist for user {}", userId);
        return watchlistRepository.getWatchlist(userId);
    }

    private Map<String, Object> mapTmdbMovie(Map<String, Object> movie) {
        Map<String, Object> result = new HashMap<>();
        result.put("tmdbId", movie.get("id"));
        result.put("title", movie.getOrDefault("title", "Unknown"));
        result.put("releaseYear", extractYear((String) movie.get("release_date")));
        result.put("rating", movie.getOrDefault("vote_average", 0.0));
        result.put("overview", movie.getOrDefault("overview", ""));
        result.put("posterPath", movie.getOrDefault("poster_path", ""));
        return result;
    }

    private String extractYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return "N/A";
        }
        return releaseDate.substring(0, 4);
    }
}
