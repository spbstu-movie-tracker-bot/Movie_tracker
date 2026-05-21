package com.movietracker.bot;

import com.movietracker.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

// Handles incoming updates and routes them
@Component
public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);

    private final TelegramBotClient botClient;
    private final UserService userService;
    private final CommandRouter commandRouter;

    public TelegramUpdateHandler(TelegramBotClient botClient, UserService userService,
                                  CommandRouter commandRouter) {
        this.botClient = botClient;
        this.userService = userService;
        this.commandRouter = commandRouter;
    }

    public Mono<Void> handleUpdate(Map<String, Object> update) {
        if (update.containsKey("message")) {
            return handleMessage((Map<String, Object>) update.get("message"));
        } else if (update.containsKey("callback_query")) {
            return handleCallbackQuery((Map<String, Object>) update.get("callback_query"));
        }
        log.debug("Ignoring update without message or callback_query: {}", update.keySet());
        return Mono.empty();
    }

    private Mono<Void> handleMessage(Map<String, Object> message) {
        Map<String, Object> from = (Map<String, Object>) message.get("from");
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        String text = (String) message.get("text");

        if (from == null || chat == null || text == null) {
            log.debug("Skipping message with null from/chat/text");
            return Mono.empty();
        }

        Long telegramId = ((Number) from.get("id")).longValue();
        Long chatId = ((Number) chat.get("id")).longValue();
        String username = (String) from.get("username");
        String firstName = (String) from.get("first_name");
        String lastName = (String) from.get("last_name");

        log.info("Message from {} (chatId={}): {}", telegramId, chatId, text);

        return userService.registerOrGetUser(telegramId, username, firstName, lastName)
                .doOnNext(user -> log.debug("User resolved: id={}, telegramId={}", user.getId(), user.getTelegramId()))
                .flatMap(user -> commandRouter.route(chatId, user, text))
                .doOnSuccess(v -> log.debug("Message handling completed for chatId={}", chatId))
                .doOnError(e -> log.error("Error handling message from chatId={}: {}", chatId, e.getMessage(), e));
    }

    private Mono<Void> handleCallbackQuery(Map<String, Object> callbackQuery) {
        String callbackId = (String) callbackQuery.get("id");
        Map<String, Object> from = (Map<String, Object>) callbackQuery.get("from");
        Map<String, Object> message = (Map<String, Object>) callbackQuery.get("message");
        String data = (String) callbackQuery.get("data");

        if (from == null || message == null || data == null) {
            return Mono.empty();
        }

        Long telegramId = ((Number) from.get("id")).longValue();
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        Long chatId = ((Number) chat.get("id")).longValue();

        log.info("Callback from {} (chatId={}): {}", telegramId, chatId, data);

        return userService.findByTelegramId(telegramId)
                .flatMap(user -> commandRouter.handleCallback(chatId, user, data))
                .then(botClient.answerCallbackQuery(callbackId, null));
    }
}
