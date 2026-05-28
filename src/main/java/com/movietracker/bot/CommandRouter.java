package com.movietracker.bot;

import com.movietracker.messaging.RecommendationProducer;
import com.movietracker.model.AppUser;
import com.movietracker.service.LlmService;
import com.movietracker.service.MovieService;
import com.movietracker.service.PreferencesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;



//// Routes commands to appropriate handlers

@Component
public class CommandRouter {

    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);
    private static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500";

    private final TelegramBotClient botClient;
    private final MovieService movieService;
    private final LlmService llmService;
    private final PreferencesService preferencesService;
    private final RecommendationProducer recommendationProducer;
    private final com.movietracker.service.UserService userService;

    public CommandRouter(TelegramBotClient botClient, MovieService movieService,
                         LlmService llmService, PreferencesService preferencesService,
                         RecommendationProducer recommendationProducer,
                         com.movietracker.service.UserService userService) {
        this.botClient = botClient;
        this.movieService = movieService;
        this.llmService = llmService;
        this.preferencesService = preferencesService;
        this.recommendationProducer = recommendationProducer;
        this.userService = userService;
    }


    public Mono<Void> route(Long chatId, AppUser user, String text) {
        if (text.startsWith("/")) {
            String[] parts = text.split(" ", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            log.info("Routing command '{}' for chatId={}", command, chatId);

            return switch (command) {
                case "/start" -> handleStart(chatId, user);
                case "/help" -> handleHelp(chatId);
                case "/search" -> handleSearch(chatId, user, args);
                case "/watchlist" -> handleWatchlist(chatId, user);
                case "/recommend" -> handleRecommend(chatId, user, args);
                case "/ask" -> handleAsk(chatId, user, args);
                // Preferences commands
                case "/setgenre" -> handleSetPreference(chatId, user, "genre", args);
                case "/setactor" -> handleSetPreference(chatId, user, "actor", args);
                case "/setdirector" -> handleSetPreference(chatId, user, "director", args);
                case "/preferences", "/prefs" -> handleShowPreferences(chatId, user);
                case "/clearprefs" -> handleClearPreferences(chatId, user, args);
                case "/setbirth" -> handleSetBirthDate(chatId, user, args);

                // Discovery commands

                case "/random" -> handleRandom(chatId, user);
                case "/similar" -> handleSimilar(chatId, user, args);
                default -> botClient.sendMessage(chatId, "Unknown command. Use /help for available commands.");

            };
        }

        // Non-command text - treat as search query
        if (!text.isBlank()) {
            return handleSearch(chatId, user, text);
        }

        return Mono.empty();
    }

    public Mono<Void> handleCallback(Long chatId, AppUser user, String data) {
        String[] parts = data.split(":", 2);
        String action = parts[0];
        String param = parts.length > 1 ? parts[1] : "";

        log.info("Handling callback action='{}' param='{}' for chatId={}", action, param, chatId);

        return switch (action) {
            case "add" -> movieService.addToWatchlist(user.getId(), Integer.parseInt(param))
                    .then(botClient.sendMessage(chatId, "✅ Movie added to your watchlist!"));
            case "remove" -> movieService.removeFromWatchlist(user.getId(), Long.parseLong(param))
                    .then(botClient.sendMessage(chatId, "🗑 Removed from watchlist."));
            case "watched" -> movieService.markAsWatched(user.getId(), Long.parseLong(param))
                    .then(botClient.sendMessage(chatId, "✔️ Marked as watched!"));
            case "detail" -> handleMovieDetail(chatId, Integer.parseInt(param));
            case "random" -> handleRandom(chatId, user);
            default -> Mono.empty();
        };
    }

    //  Core Commands

    private Mono<Void> handleStart(Long chatId, AppUser user) {
        String name = user.getFirstName() != null ? user.getFirstName() : "there";
        String message = String.format("""
                👋 Welcome, %s!
                
                I'm your Movie Tracker bot. I can help you:
                • 🔍 Search for movies
                • 📋 Manage your watchlist
                • ⚙️ Set your preferences (genres, actors, directors)
                • 🎂 Set your birth date for age-restricted features
                • 🎬 Get personalized recommendations
                • 🎲 Discover random movies
                • 💬 Ask questions about movies (AI-powered)
                
                Use /help to see all commands.""", name);

        log.info("Sending welcome message to chatId={}", chatId);
        return botClient.sendMessage(chatId, message);
    }

    private Mono<Void> handleHelp(Long chatId) {
        String helpText = """
                📚 <b>Available Commands</b>
                
                <b>🔍 Search</b>
                Just type the name of the movie you're looking for — no command needed!
                /random - Get a random movie suggestion
                /similar [title] - Find similar movies (AI)
                
                <b>📋 Watchlist</b>
                /watchlist - View your watchlist
                
                <b>⚙️ Preferences</b>
                /setgenre [genres] - Set favorite genres
                /setactor [actors] - Set favorite actors
                /setdirector [directors] - Set favorite directors
                /preferences - Show your preferences
                /clearprefs - Clear all preferences
                /setbirth [DD.MM.YYYY] - Set your birth date
                
                <b>🤖 AI Features</b>
                /recommend - AI recommendations (uses watchlist + preferences)
                /recommend [text] - Describe what you want
                /ask [question] - Ask about movies
                
                💡 <i>Tip: separate multiple values with commas, e.g.</i>
                <code>/setgenre action, comedy, thriller</code>""";

        return botClient.sendMessage(chatId, helpText);
    }

    //  Search Commands 

    private Mono<Void> handleSearch(Long chatId, AppUser user, String query) {
        if (query.isBlank()) {
            // Use preferences if no query provided
            return preferencesService.getPreferencesByType(user.getId(), "genre")
                    .flatMap(genres -> {
                        if (genres.isEmpty()) {
                            return botClient.sendMessage(chatId,
                                    "Please provide a search query: /search [movie name]\n" +
                                    "Or set genre preferences with /setgenre and search without a query.");
                        }
                        // Search using preferred genres via discover
                        return movieService.discoverMovies(genres)
                                .take(5)
                                .collectList()
                                .flatMap(movies -> formatSearchResults(chatId, movies, "Movies matching your preferences"));
                    });
        }

        return movieService.searchMovies(query)
                .take(5)
                .collectList()
                .flatMap(movies -> {
                    if (movies.isEmpty()) {
                        return botClient.sendMessage(chatId, "No movies found for: " + query);
                    }
                    return formatSearchResults(chatId, movies, "Results for: " + query);
                });
    }

   
   // Show movie detail card with poster and add button

    private Mono<Void> handleMovieDetail(Long chatId, Integer tmdbId) {
        return movieService.getMovieDetails(tmdbId)
                .flatMap(movie -> {
                    Object ratingObj = movie.get("rating");
                    double rating = ratingObj instanceof Number n ? n.doubleValue() : 0.0;
                    Object runtimeObj = movie.get("runtime");
                    int runtime = runtimeObj instanceof Number n ? n.intValue() : 0;

                    String caption = String.format("""
                            <b>%s</b>
                            
                            ⭐ Rating: %.1f/10
                            📅 Release: %s
                            🕐 Runtime: %d min
                            
                            %s""",
                            movie.get("title"),
                            rating,
                            movie.getOrDefault("releaseDate", movie.get("releaseYear")),
                            runtime,
                            movie.get("overview"));

                    List<List<Map<String, String>>> keyboard = List.of(
                            List.of(Map.of(
                                    "text", "➕ Add to Watchlist",
                                    "callback_data", "add:" + tmdbId
                            ))
                    );

                    // Try to send photo with poster, fallback to text
                    String posterPath = (String) movie.get("posterPath");
                    if (posterPath != null && !posterPath.isEmpty()) {
                        String posterUrl = TMDB_IMAGE_BASE + posterPath;
                        return botClient.sendPhoto(chatId, posterUrl, (String) movie.get("title"))
                                .then(botClient.sendMessageWithKeyboard(chatId, caption, keyboard))
                                .thenReturn("done");
                    } else {
                        return botClient.sendMessageWithKeyboard(chatId, caption, keyboard)
                                .thenReturn("done");
                    }
                })
                .switchIfEmpty(Mono.defer(() ->
                        botClient.sendMessage(chatId, "Movie details not available.")
                                .thenReturn("error")
                ))
                .then();
    }

    private Mono<Void> handleRandom(Long chatId, AppUser user) {
        // Use user's genre preferences for random selection if available
        return preferencesService.getPreferencesByType(user.getId(), "genre")
                .flatMap(genres -> movieService.getRandomMovie(genres))
                .switchIfEmpty(movieService.getRandomMovie(List.of()))
                .flatMap(movie -> {
                    Object ratingObj = movie.get("rating");
                    double rating = ratingObj instanceof Number n ? n.doubleValue() : 0.0;
                    Integer tmdbId = ((Number) movie.get("tmdbId")).intValue();

                    String overview = (String) movie.get("overview");
                    if (overview != null && overview.length() > 300) {
                        overview = overview.substring(0, 300) + "...";
                    }

                    String caption = String.format("""
                            🎲 <b>Random Movie Suggestion:</b>
                            
                            🎬 <b>%s</b> (%s)
                            ⭐ %.1f
                            
                            📝 %s""",
                            movie.get("title"),
                            movie.get("releaseYear"),
                            rating,
                            overview);

                    List<List<Map<String, String>>> keyboard = List.of(
                            List.of(
                                    Map.of("text", "ℹ️ Details", "callback_data", "detail:" + tmdbId),
                                    Map.of("text", "➕ Add to watchlist", "callback_data", "add:" + tmdbId)
                            ),
                            List.of(Map.of("text", "🎲 Another random", "callback_data", "random:0"))
                    );

                    String posterPath = (String) movie.get("posterPath");
                    if (posterPath != null && !posterPath.isEmpty()) {
                        String posterUrl = TMDB_IMAGE_BASE + posterPath;
                        return botClient.sendPhoto(chatId, posterUrl, (String) movie.get("title"))
                                .then(botClient.sendMessageWithKeyboard(chatId, caption, keyboard))
                                .thenReturn("done");
                    }
                    return botClient.sendMessageWithKeyboard(chatId, caption, keyboard)
                            .thenReturn("done");
                })
                .switchIfEmpty(Mono.defer(() ->
                        botClient.sendMessage(chatId, "Could not find a movie. Please try again.")
                                .thenReturn("error")
                ))
                .then();
    }

    private Mono<Void> handleSimilar(Long chatId, AppUser user, String movieTitle) {
        if (movieTitle.isBlank()) {
            return botClient.sendMessage(chatId, "Please provide a movie title: /similar [movie title]");
        }

        return botClient.sendMessage(chatId, "🤔 Finding movies similar to \"" + movieTitle + "\"...")
                .then(llmService.findSimilar(movieTitle))
                .flatMap(result -> botClient.sendMessage(chatId, "🎬 <b>Similar to \"" + movieTitle + "\":</b>\n\n" + result));
    }

    //  Preferences Commands

    private Mono<Void> handleSetPreference(Long chatId, AppUser user, String type, String values) {
        if (values.isBlank()) {
            String examples = switch (type) {
                case "genre" -> "<code>/setgenre action, comedy, thriller</code>";
                case "actor" -> "<code>/setactor Tom Hanks, Leonardo DiCaprio</code>";
                case "director" -> "<code>/setdirector Christopher Nolan, Martin Scorsese</code>";
                default -> "";
            };
            return botClient.sendMessage(chatId,
                    "Please provide values, e.g.:\n" + examples);
        }

        return preferencesService.setPreferences(user.getId(), type, values)
                .flatMap(savedValues -> {
                    String emoji = switch (type) {
                        case "genre" -> "🎭";
                        case "actor" -> "⭐";
                        case "director" -> "🎬";
                        default -> "✅";
                    };
                    return botClient.sendMessage(chatId,
                            emoji + " <b>" + capitalize(type) + "s</b> set: " + String.join(", ", savedValues));
                });
    }

    private Mono<Void> handleShowPreferences(Long chatId, AppUser user) {
        return preferencesService.formatPreferences(user.getId())
                .flatMap(formatted -> botClient.sendMessage(chatId,
                        "⚙️ <b>Your Preferences:</b>\n\n" + formatted));
    }

    private Mono<Void> handleClearPreferences(Long chatId, AppUser user, String type) {
        if (type.isBlank()) {
            // Clear all preferences
            return preferencesService.clearAllPreferences(user.getId())
                    .then(botClient.sendMessage(chatId, "🗑 All preferences cleared."));
        }

        String cleanType = type.trim().toLowerCase();
        if (!cleanType.equals("genre") && !cleanType.equals("actor") && !cleanType.equals("director")) {
            return botClient.sendMessage(chatId,
                    "Invalid type. Use: /clearprefs [genre|actor|director] or /clearprefs to clear all.");
        }

        return preferencesService.clearPreferencesByType(user.getId(), cleanType)
                .then(botClient.sendMessage(chatId, "🗑 " + capitalize(cleanType) + " preferences cleared."));
    }
    private Mono<Void> handleSetBirthDate(Long chatId, AppUser user, String dateStr) {
        if (dateStr.isBlank()) {
            return botClient.sendMessage(chatId,
                    "Please provide your birth date in format DD.MM.YYYY\n" +
                            "Example: <code>/setbirth 15.03.1999</code>");
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate birthDate = LocalDate.parse(dateStr.trim(), formatter);

            // Validate: not in the future
            if (birthDate.isAfter(LocalDate.now())) {
                return botClient.sendMessage(chatId, "❌ Birth date cannot be in the future.");
            }

            // Validate: reasonable age (not older than 150 years)
            if (birthDate.isBefore(LocalDate.now().minusYears(150))) {
                return botClient.sendMessage(chatId, "❌ Please enter a valid birth date.");
            }

            return userService.updateBirthDate(user.getId(), birthDate)
                    .then(botClient.sendMessage(chatId,
                            "🎂 Birth date set to: " + birthDate.format(formatter)));
        } catch (Exception e) {
            return botClient.sendMessage(chatId,
                    "❌ Invalid date format. Please use DD.MM.YYYY\n" +
                            "Example: <code>/setbirth 15.03.1999</code>");
        }
    }

    //  Watchlist Command 

    private Mono<Void> handleWatchlist(Long chatId, AppUser user) {
        return movieService.getWatchlist(user.getId())
                .collectList()
                .flatMap(movies -> {
                    if (movies.isEmpty()) {
                        return botClient.sendMessage(chatId, "Your watchlist is empty. Use /search to find movies!");
                    }

                    StringBuilder sb = new StringBuilder("📋 <b>Your Watchlist:</b>\n\n");
                    List<List<Map<String, String>>> keyboard = new ArrayList<>();

                    for (Map<String, Object> movie : movies) {
                        Object rawWatched = movie.get("watched");
                        boolean watched = rawWatched instanceof Boolean b && b;
                        String status = watched ? "✅" : "⏳";
                        sb.append(String.format("%s <b>%s</b>\n", status, movie.get("title")));

                        // Add action buttons
                        Long watchlistId = ((Number) movie.get("watchlistId")).longValue();
                        List<Map<String, String>> row = new ArrayList<>();
                        if (!watched) {
                            row.add(Map.of(
                                    "text", "✔️ Watched",
                                    "callback_data", "watched:" + watchlistId
                            ));
                        }
                        row.add(Map.of(
                                "text", "🗑 Remove",
                                "callback_data", "remove:" + watchlistId
                        ));
                        keyboard.add(row);
                    }

                    return botClient.sendMessageWithKeyboard(chatId, sb.toString(), keyboard);
                });
    }

    // ── AI Commands (recommendations routed through RabbitMQ) ─────────

    private Mono<Void> handleRecommend(Long chatId, AppUser user, String args) {
        // If user provided a specific request, publish to RabbitMQ with direct text
        if (!args.isBlank()) {
            return botClient.sendMessage(chatId, " Your request has been queued for processing...")
                    .then(recommendationProducer.publishRecommendationTask(
                            chatId, user.getId(), "", "", args));
        }

        // Without args, gather watchlist + preferences and publish to RabbitMQ
        return movieService.getWatchlist(user.getId())
                .collectList()
                .flatMap(watchlist -> {
                    if (watchlist.isEmpty()) {
                        return botClient.sendMessage(chatId,
                                "Add some movies to your watchlist first, or use: /recommend [describe what you want]");
                    }

                    // Collect movie titles for the message payload
                    String movieTitles = watchlist.stream()
                            .map(m -> (String) m.get("title"))
                            .collect(Collectors.joining(", "));

                    // Get user preferences and publish task to RabbitMQ
                    return preferencesService.buildPreferenceSummary(user.getId())
                            .flatMap(prefSummary ->
                                botClient.sendMessage(chatId, "🤔 Your recommendation request has been queued...")
                                    .then(recommendationProducer.publishRecommendationTask(
                                            chatId, user.getId(), movieTitles, prefSummary, "")));
                });
    }

    private Mono<Void> handleAsk(Long chatId, AppUser user, String question) {
        if (question.isBlank()) {
            return botClient.sendMessage(chatId, "Please provide a question: /ask [your question]");
        }

        return botClient.sendMessage(chatId, "🤔 Thinking...")
                .then(llmService.askAboutMovies(question))
                .flatMap(answer -> botClient.sendMessage(chatId, "🤖 " + answer));
    }

    //  Helpers 


    // Format search results as a list with buttons

    private Mono<Void> formatSearchResults(Long chatId, List<Map<String, Object>> movies, String header) {
        if (movies.isEmpty()) {
            return botClient.sendMessage(chatId, "No movies found.");
        }

        String text = "🔍 <b>" + header + "</b>";
        List<List<Map<String, String>>> keyboard = new ArrayList<>();

        for (Map<String, Object> movie : movies) {
            String title = (String) movie.get("title");
            String year = (String) movie.get("releaseYear");
            Integer tmdbId = ((Number) movie.get("tmdbId")).intValue();

            // Each movie is a button that opens detail view
            keyboard.add(List.of(Map.of(
                    "text", title + " (" + year + ")",
                    "callback_data", "detail:" + tmdbId
            )));
        }

        return botClient.sendMessageWithKeyboard(chatId, text, keyboard);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
