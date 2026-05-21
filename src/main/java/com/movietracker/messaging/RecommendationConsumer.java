package com.movietracker.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movietracker.bot.TelegramBotClient;
import com.movietracker.config.RabbitMqConfig;
import com.movietracker.service.LlmService;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

// Consumes recommendation tasks from RabbitMQ and sends results via Telegram
@Component
public class RecommendationConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationConsumer.class);

    private final Channel channel;
    private final LlmService llmService;
    private final TelegramBotClient botClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String consumerTag;

    public RecommendationConsumer(Channel channel, LlmService llmService, TelegramBotClient botClient) {
        this.channel = channel;
        this.llmService = llmService;
        this.botClient = botClient;
    }

    @PostConstruct
    public void startConsuming() throws IOException {
        if (!llmService.isConfigured()) {
            log.warn("LLM not configured — recommendation consumer will not start");
            return;
        }

        // Prefetch 1 message at a time (fair dispatch)
        channel.basicQos(1);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String json = new String(delivery.getBody(), StandardCharsets.UTF_8);
            log.info("Received recommendation task: {}", json);

            try {
                Map<String, Object> task = objectMapper.readValue(json, Map.class);
                processTask(task);

                // Acknowledge the message after successful processing
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                log.debug("Task acknowledged: {}", delivery.getEnvelope().getDeliveryTag());
            } catch (Exception e) {
                log.error("Error processing recommendation task: {}", e.getMessage(), e);
                // Reject and do not requeue (to avoid infinite loop on bad messages)
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            }
        };

        // Start consuming (manual acknowledgment mode: autoAck = false)
        consumerTag = channel.basicConsume(
                RabbitMqConfig.RECOMMENDATION_QUEUE,
                false,
                deliverCallback,
                cancelTag -> log.warn("Consumer cancelled: {}", cancelTag)
        );

        log.info("RecommendationConsumer started, listening on queue '{}'", RabbitMqConfig.RECOMMENDATION_QUEUE);
    }

    @PreDestroy
    public void stopConsuming() {
        if (consumerTag != null) {
            try {
                channel.basicCancel(consumerTag);
                log.info("RecommendationConsumer stopped");
            } catch (IOException e) {
                log.error("Error stopping consumer: {}", e.getMessage(), e);
            }
        }
    }

    private void processTask(Map<String, Object> task) {
        Long chatId = ((Number) task.get("chatId")).longValue();
        String directRequest = (String) task.getOrDefault("directRequest", "");
        String movieTitles = (String) task.getOrDefault("movieTitles", "");
        String preferenceSummary = (String) task.getOrDefault("preferenceSummary", "");

        if (!directRequest.isEmpty()) {
            // Direct request mode: "/recommend sci-fi comedy"
            llmService.askAboutMovies("Recommend movies: " + directRequest)
                    .flatMap(answer -> botClient.sendMessage(chatId,
                            "🎬 <b>AI Recommendations:</b>\n\n" + answer))
                    .doOnSuccess(v -> log.info("Recommendation sent to chatId={}", chatId))
                    .doOnError(e -> log.error("Failed to send recommendation to chatId={}: {}",
                            chatId, e.getMessage()))
                    .subscribe();
        } else if (!movieTitles.isEmpty()) {
            // Watchlist-based mode: build context from watchlist + preferences
            List<Map<String, Object>> watchlistData = List.of(Map.of("title", movieTitles));
            llmService.getRecommendations(watchlistData, preferenceSummary)
                    .flatMap(recommendations -> botClient.sendMessage(chatId,
                            "🎬 <b>AI Recommendations:</b>\n\n" + recommendations))
                    .doOnSuccess(v -> log.info("Recommendation sent to chatId={}", chatId))
                    .doOnError(e -> log.error("Failed to send recommendation to chatId={}: {}",
                            chatId, e.getMessage()))
                    .subscribe();
        } else {
            botClient.sendMessage(chatId, "Could not generate recommendations. " +
                    "Please add movies to your watchlist first.").subscribe();
        }
    }
}
