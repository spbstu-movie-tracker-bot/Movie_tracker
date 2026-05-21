package com.movietracker.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// Long-polling service for Telegram updates
@Service
public class BotPollingService {

    private static final Logger log = LoggerFactory.getLogger(BotPollingService.class);

    private final TelegramBotClient botClient;
    private final TelegramUpdateHandler updateHandler;
    private final AtomicLong offset = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BotPollingService(TelegramBotClient botClient, TelegramUpdateHandler updateHandler) {
        this.botClient = botClient;
        this.updateHandler = updateHandler;
    }

    @PostConstruct
    public void start() {
        if (!botClient.isConfigured()) {
            log.warn("Telegram bot not configured - polling disabled");
            return;
        }

        running.set(true);
        log.info("Starting Telegram bot polling...");

        botClient.deleteWebhook()
                .then(Mono.defer(this::poll)
                        .repeat(() -> running.get())
                        .then())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> {},
                        e -> log.error("Polling error", e),
                        () -> log.info("Polling stopped")
                );
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping Telegram bot polling...");
        running.set(false);
    }

    private Mono<Void> poll() {
        return botClient.getUpdates(offset.get())
                .flatMap(response -> {
                    if (response == null || !(Boolean) response.getOrDefault("ok", false)) {
                        log.warn("Telegram API returned non-ok response: {}", response);
                        return Mono.delay(Duration.ofSeconds(5)).then();
                    }

                    List<Map<String, Object>> updates = (List<Map<String, Object>>) response.get("result");
                    if (updates == null || updates.isEmpty()) {
                        return Mono.empty();
                    }

                    log.debug("Received {} updates", updates.size());
                    return processUpdates(updates);
                })
                .onErrorResume(e -> {
                    log.error("Error during polling: {}", e.getMessage(), e);
                    return Mono.delay(Duration.ofSeconds(5)).then();
                });
    }

    private Mono<Void> processUpdates(List<Map<String, Object>> updates) {
        return Flux.fromIterable(updates)
                .concatMap(update -> {
                    Number updateId = (Number) update.get("update_id");
                    if (updateId != null) {
                        offset.set(updateId.longValue() + 1);
                    }

                    return updateHandler.handleUpdate(update)
                            .doOnSuccess(v -> log.debug("Update {} processed successfully",
                                    updateId != null ? updateId : "unknown"))
                            .onErrorResume(e -> {
                                log.error("Error handling update {}: {}",
                                        updateId != null ? updateId : "unknown", e.getMessage(), e);
                                return Mono.empty();
                            });
                })
                .then();
    }
}
