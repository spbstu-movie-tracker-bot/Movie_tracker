package com.movietracker.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.movietracker.db.JooqTables.*;

// Movie + watchlist data access with jOOQ
@Repository
public class WatchlistRepository {

    private static final Logger log = LoggerFactory.getLogger(WatchlistRepository.class);
    private final DSLContext dsl;

    // Table-qualified fields to avoid ambiguity in JOIN queries
    private static final Field<Long> W_ID = DSL.field(DSL.name("watchlist", "id"), Long.class);
    private static final Field<Long> W_USER_ID = DSL.field(DSL.name("watchlist", "user_id"), Long.class);
    private static final Field<Long> W_MOVIE_ID = DSL.field(DSL.name("watchlist", "movie_id"), Long.class);
    private static final Field<LocalDateTime> W_ADDED_AT = DSL.field(DSL.name("watchlist", "added_at"), LocalDateTime.class);
    private static final Field<Boolean> W_WATCHED = DSL.field(DSL.name("watchlist", "watched"), Boolean.class);

    private static final Field<Long> M_ID = DSL.field(DSL.name("movies", "id"), Long.class);
    private static final Field<Integer> M_TMDB_ID = DSL.field(DSL.name("movies", "tmdb_id"), Integer.class);
    private static final Field<String> M_TITLE = DSL.field(DSL.name("movies", "title"), String.class);
    private static final Field<String> M_OVERVIEW = DSL.field(DSL.name("movies", "overview"), String.class);
    private static final Field<Double> M_VOTE_AVG = DSL.field(DSL.name("movies", "vote_average"), Double.class);

    public WatchlistRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // Find or create movie by TMDb ID, returns local DB id
    public Mono<Long> findOrCreateMovie(Map<String, Object> movieData) {
        Integer tmdbId = ((Number) movieData.get("tmdbId")).intValue();
        String title = (String) movieData.getOrDefault("title", "Unknown");
        String overview = (String) movieData.getOrDefault("overview", "");
        String posterPath = (String) movieData.getOrDefault("posterPath", "");
        Object ratingObj = movieData.getOrDefault("rating", 0.0);
        Double rating = ratingObj instanceof Number n ? n.doubleValue() : 0.0;
        String releaseYear = (String) movieData.getOrDefault("releaseYear", "");

        // Try to find existing movie first
        return Mono.from(
                dsl.selectFrom(MOVIES)
                        .where(MOVIES_TMDB_ID.eq(tmdbId))
        ).map(record -> record.get(MOVIES_ID))
        .switchIfEmpty(Mono.defer(() -> {
            log.info("Saving new movie: {} (tmdbId={})", title, tmdbId);

            LocalDate releaseDate = null;
            try {
                if (releaseYear != null && releaseYear.length() == 4) {
                    releaseDate = LocalDate.of(Integer.parseInt(releaseYear), 1, 1);
                }
            } catch (NumberFormatException ignored) {}

            var insertQuery = dsl.insertInto(MOVIES)
                    .set(MOVIES_TMDB_ID, tmdbId)
                    .set(MOVIES_TITLE, title)
                    .set(MOVIES_ORIGINAL_TITLE, title)
                    .set(MOVIES_OVERVIEW, overview)
                    .set(MOVIES_POSTER_PATH, posterPath)
                    .set(MOVIES_VOTE_AVERAGE, rating)
                    .set(MOVIES_CREATED_AT, LocalDateTime.now());

            if (releaseDate != null) {
                insertQuery = insertQuery.set(MOVIES_RELEASE_DATE, releaseDate);
            }

            return Mono.from(insertQuery.returning(MOVIES_ID))
                    .map(record -> record.get(MOVIES_ID));
        }));
    }

    // Add movie to user's watchlist
    public Mono<Void> addToWatchlist(Long userId, Long movieId) {
        return Mono.from(
                dsl.insertInto(WATCHLIST)
                        .set(WATCHLIST_USER_ID, userId)
                        .set(WATCHLIST_MOVIE_ID, movieId)
                        .set(WATCHLIST_ADDED_AT, LocalDateTime.now())
                        .set(WATCHLIST_WATCHED, false)
                        .onConflictDoNothing()
        ).then();
    }

    // Remove movie from watchlist
    public Mono<Void> removeFromWatchlist(Long userId, Long watchlistId) {
        return Mono.from(
                dsl.deleteFrom(WATCHLIST)
                        .where(WATCHLIST_ID.eq(watchlistId))
                        .and(WATCHLIST_USER_ID.eq(userId))
        ).then();
    }

    // Mark as watched
    public Mono<Void> markAsWatched(Long userId, Long watchlistId) {
        return Mono.from(
                dsl.update(WATCHLIST)
                        .set(WATCHLIST_WATCHED, true)
                        .where(WATCHLIST_ID.eq(watchlistId))
                        .and(WATCHLIST_USER_ID.eq(userId))
        ).then();
    }

    // Get user's watchlist joined with movie details
    public Flux<Map<String, Object>> getWatchlist(Long userId) {
        return Flux.from(
                dsl.select(
                        W_ID.as("watchlist_id"),
                        W_WATCHED.as("watched"),
                        W_ADDED_AT.as("added_at"),
                        M_TITLE.as("title"),
                        M_TMDB_ID.as("tmdb_id"),
                        M_VOTE_AVG.as("vote_average"),
                        M_OVERVIEW.as("overview")
                ).from(WATCHLIST)
                .join(MOVIES).on(W_MOVIE_ID.eq(M_ID))
                .where(W_USER_ID.eq(userId))
                .orderBy(W_ADDED_AT.desc())
        ).map(record -> {
            Object rawWatched = record.get("watched");
            boolean watched = rawWatched instanceof Boolean b ? b : false;

            Object rawRating = record.get("vote_average");
            double rating = rawRating instanceof Number n ? n.doubleValue() : 0.0;

            Object rawWatchlistId = record.get("watchlist_id");
            long watchlistId = rawWatchlistId instanceof Number n ? n.longValue() : 0L;

            Object rawTmdbId = record.get("tmdb_id");
            int tmdbId = rawTmdbId instanceof Number n ? n.intValue() : 0;

            // Use HashMap to allow null values (Map.of() does not allow nulls)
            Map<String, Object> result = new HashMap<>();
            result.put("watchlistId", watchlistId);
            result.put("title", record.get("title") != null ? record.get("title") : "Unknown");
            result.put("tmdbId", tmdbId);
            result.put("rating", rating);
            result.put("watched", watched);
            result.put("overview", record.get("overview") != null ? record.get("overview") : "");
            return result;
        })
        .doOnError(e -> log.error("Error fetching watchlist for user {}: {}", userId, e.getMessage(), e));
    }
}
