package com.movietracker.repository;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.movietracker.db.JooqTables.*;

// Repository for user preferences (favorite genres, actors, directors).

@Repository
public class PreferencesRepository {

    private static final Logger log = LoggerFactory.getLogger(PreferencesRepository.class);
    private final DSLContext dsl;

    public PreferencesRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // Add a preference for a user
    public Mono<Void> addPreference(Long userId, String prefType, String prefValue) {
        return Mono.from(
                dsl.insertInto(PREFERENCES)
                        .set(PREFERENCES_USER_ID, userId)
                        .set(PREFERENCES_PREF_TYPE, prefType)
                        .set(PREFERENCES_PREF_VALUE, prefValue)
                        .set(PREFERENCES_CREATED_AT, LocalDateTime.now())
                        .onConflictDoNothing()
        ).doOnNext(r -> log.debug("Added preference: {} = {} for user {}", prefType, prefValue, userId))
         .then();
    }

    // Get preferences, optionally filtered by type
    public Flux<Map<String, String>> getPreferences(Long userId, String prefType) {
        var query = dsl.selectFrom(PREFERENCES)
                .where(PREFERENCES_USER_ID.eq(userId));

        if (prefType != null) {
            query = query.and(PREFERENCES_PREF_TYPE.eq(prefType));
        }

        return Flux.from(query.orderBy(PREFERENCES_PREF_TYPE, PREFERENCES_PREF_VALUE))
                .map(record -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("type", record.get(PREFERENCES_PREF_TYPE));
                    result.put("value", record.get(PREFERENCES_PREF_VALUE));
                    return result;
                });
    }

    // Get all preferences for a user
    public Flux<Map<String, String>> getAllPreferences(Long userId) {
        return getPreferences(userId, null);
    }

    // Delete specific preference by type and value
    public Mono<Integer> deletePreference(Long userId, String prefType, String prefValue) {
        return Mono.from(
                dsl.deleteFrom(PREFERENCES)
                        .where(PREFERENCES_USER_ID.eq(userId))
                        .and(PREFERENCES_PREF_TYPE.eq(prefType))
                        .and(PREFERENCES_PREF_VALUE.eq(prefValue))
        );
    }

    // Delete all preferences of a given type
    public Mono<Integer> deletePreferencesByType(Long userId, String prefType) {
        return Mono.from(
                dsl.deleteFrom(PREFERENCES)
                        .where(PREFERENCES_USER_ID.eq(userId))
                        .and(PREFERENCES_PREF_TYPE.eq(prefType))
        );
    }

    // Delete all preferences for a user
    public Mono<Integer> deleteAllPreferences(Long userId) {
        return Mono.from(
                dsl.deleteFrom(PREFERENCES)
                        .where(PREFERENCES_USER_ID.eq(userId))
        );
    }
}
