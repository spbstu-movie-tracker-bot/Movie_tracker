package com.movietracker.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

// Telegram Bot API client
@Component
public class TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotClient.class);
    private final WebClient webClient;
    private final String botToken;

    public TelegramBotClient() {
        this.botToken = System.getenv().getOrDefault("TELEGRAM_BOT_TOKEN", "");
        this.webClient = WebClient.builder()
                .baseUrl("https://api.telegram.org/bot" + botToken)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        if (botToken.isEmpty()) {
            log.warn("TELEGRAM_BOT_TOKEN not set!");
        }
    }

    public Mono<Map> getUpdates(Long offset) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getUpdates")
                        .queryParam("offset", offset)
                        .queryParam("timeout", 30)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .doOnError(e -> log.error("Failed to get updates", e));
    }

    public Mono<Void> sendMessage(Long chatId, String text) {
        log.debug("Sending message to chatId={}: {}", chatId, text.substring(0, Math.min(text.length(), 80)));
        return webClient.post()
                .uri("/sendMessage")
                .bodyValue(Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "parse_mode", "HTML"
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(resp -> log.debug("sendMessage response: {}", resp))
                .doOnError(e -> log.error("Failed to send message to {}", chatId, e))
                .then();
    }

    public Mono<Void> sendMessageWithKeyboard(Long chatId, String text, List<List<Map<String, String>>> keyboard) {
        return webClient.post()
                .uri("/sendMessage")
                .bodyValue(Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "parse_mode", "HTML",
                        "reply_markup", Map.of("inline_keyboard", keyboard)
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(resp -> log.debug("sendMessageWithKeyboard response: {}", resp))
                .doOnError(e -> log.error("Failed to send message with keyboard to {}", chatId, e))
                .then();
    }

    public Mono<Void> sendPhoto(Long chatId, String photoUrl, String caption) {
        return webClient.post()
                .uri("/sendPhoto")
                .bodyValue(Map.of(
                        "chat_id", chatId,
                        "photo", photoUrl,
                        "caption", caption,
                        "parse_mode", "HTML"
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(resp -> log.debug("sendPhoto response: {}", resp))
                .doOnError(e -> log.error("Failed to send photo to {}", chatId, e))
                .then();
    }

    public Mono<Void> answerCallbackQuery(String callbackQueryId, String text) {
        return webClient.post()
                .uri("/answerCallbackQuery")
                .bodyValue(Map.of(
                        "callback_query_id", callbackQueryId,
                        "text", text != null ? text : ""
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.error("Failed to answer callback query {}", callbackQueryId, e))
                .then();
    }

    public Mono<Void> deleteWebhook() {
        return webClient.post()
                .uri("/deleteWebhook")
                .bodyValue(Map.of("drop_pending_updates", true))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(resp -> log.info("Webhook deleted: {}", resp))
                .doOnError(e -> log.error("Failed to delete webhook", e))
                .then();
    }

    public boolean isConfigured() {
        return !botToken.isEmpty();
    }
}
