package com.movietracker.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movietracker.config.RabbitMqConfig;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

// Sends recommendation tasks to RabbitMQ
@Component
public class RecommendationProducer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationProducer.class);
    private final Channel channel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecommendationProducer(Channel channel) {
        this.channel = channel;
    }

    // Publishes a recommendation task to the queue
    public Mono<Void> publishRecommendationTask(Long chatId, Long userId,
                                                  String movieTitles, String preferenceSummary,
                                                  String directRequest) {
        return Mono.fromCallable(() -> {
            Map<String, Object> task = Map.of(
                    "chatId", chatId,
                    "userId", userId,
                    "movieTitles", movieTitles != null ? movieTitles : "",
                    "preferenceSummary", preferenceSummary != null ? preferenceSummary : "",
                    "directRequest", directRequest != null ? directRequest : ""
            );

            String json = objectMapper.writeValueAsString(task);
            channel.basicPublish(
                    RabbitMqConfig.EXCHANGE_NAME,
                    RabbitMqConfig.RECOMMENDATION_ROUTING_KEY,
                    null,
                    json.getBytes(StandardCharsets.UTF_8)
            );

            log.info("Published recommendation task for chatId={}, userId={}", chatId, userId);
            return json;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnError(e -> log.error("Failed to publish recommendation task: {}", e.getMessage(), e))
        .then();
    }
}
