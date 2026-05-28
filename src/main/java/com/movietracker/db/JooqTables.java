package com.movietracker.db;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.time.LocalDate;
import java.time.LocalDateTime;

//jOOQ table and field definitions for type-safe SQL queries.

public final class JooqTables {

    private JooqTables() {}

    // USERS table 
    public static final Table<Record> USERS = DSL.table("users");
    public static final Field<Long> USERS_ID = DSL.field("id", Long.class);
    public static final Field<Long> USERS_TELEGRAM_ID = DSL.field("telegram_id", Long.class);
    public static final Field<String> USERS_USERNAME = DSL.field("username", String.class);
    public static final Field<String> USERS_FIRST_NAME = DSL.field("first_name", String.class);
    public static final Field<String> USERS_LAST_NAME = DSL.field("last_name", String.class);
    public static final Field<String> USERS_ROLE = DSL.field("role", String.class);
    public static final Field<LocalDate> USERS_BIRTH_DATE = DSL.field("birth_date", LocalDate.class);
    public static final Field<LocalDateTime> USERS_REGISTERED_AT = DSL.field("registered_at", LocalDateTime.class);
    public static final Field<Boolean> USERS_IS_ACTIVE = DSL.field("is_active", Boolean.class);

    // MOVIES table
    public static final Table<Record> MOVIES = DSL.table("movies");
    public static final Field<Long> MOVIES_ID = DSL.field("id", Long.class);
    public static final Field<Integer> MOVIES_TMDB_ID = DSL.field("tmdb_id", Integer.class);
    public static final Field<String> MOVIES_TITLE = DSL.field("title", String.class);
    public static final Field<String> MOVIES_ORIGINAL_TITLE = DSL.field("original_title", String.class);
    public static final Field<String> MOVIES_OVERVIEW = DSL.field("overview", String.class);
    public static final Field<String> MOVIES_POSTER_PATH = DSL.field("poster_path", String.class);
    public static final Field<LocalDate> MOVIES_RELEASE_DATE = DSL.field("release_date", LocalDate.class);
    public static final Field<Double> MOVIES_VOTE_AVERAGE = DSL.field("vote_average", Double.class);
    public static final Field<Double> MOVIES_POPULARITY = DSL.field("popularity", Double.class);
    public static final Field<Integer> MOVIES_RUNTIME = DSL.field("runtime", Integer.class);
    public static final Field<LocalDateTime> MOVIES_CREATED_AT = DSL.field("created_at", LocalDateTime.class);

    // WATCHLIST table
    public static final Table<Record> WATCHLIST = DSL.table("watchlist");
    public static final Field<Long> WATCHLIST_ID = DSL.field("id", Long.class);
    public static final Field<Long> WATCHLIST_USER_ID = DSL.field("user_id", Long.class);
    public static final Field<Long> WATCHLIST_MOVIE_ID = DSL.field("movie_id", Long.class);
    public static final Field<LocalDateTime> WATCHLIST_ADDED_AT = DSL.field("added_at", LocalDateTime.class);
    public static final Field<Boolean> WATCHLIST_WATCHED = DSL.field("watched", Boolean.class);

    // PREFERENCES table
    public static final Table<Record> PREFERENCES = DSL.table("preferences");
    public static final Field<Long> PREFERENCES_ID = DSL.field("id", Long.class);
    public static final Field<Long> PREFERENCES_USER_ID = DSL.field("user_id", Long.class);
    public static final Field<String> PREFERENCES_PREF_TYPE = DSL.field("pref_type", String.class);
    public static final Field<String> PREFERENCES_PREF_VALUE = DSL.field("pref_value", String.class);
    public static final Field<LocalDateTime> PREFERENCES_CREATED_AT = DSL.field("created_at", LocalDateTime.class);
}
