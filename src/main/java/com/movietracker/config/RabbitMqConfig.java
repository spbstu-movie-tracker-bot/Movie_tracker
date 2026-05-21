package com.movietracker.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeoutException;


////RabbitMQ configuration using direct AMQP client 

////Declares exchange, queue, and binding for recommendation tasks
@Configuration
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    public static final String EXCHANGE_NAME = "movie.tracker";
    public static final String RECOMMENDATION_QUEUE = "recommendation.tasks";
    public static final String RECOMMENDATION_ROUTING_KEY = "recommendation.request";

    private final String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private final int port = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private final String username = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
    private final String password = System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest");

    @Bean
    public ConnectionFactory rabbitConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        log.info("RabbitMQ ConnectionFactory configured for {}:{}", host, port);
        return factory;
    }

    @Bean
    public Connection rabbitConnection(ConnectionFactory rabbitConnectionFactory)
            throws IOException, TimeoutException, InterruptedException {
        int maxRetries = 5;
        for (int i = 1; i <= maxRetries; i++) {
            try {
                Connection connection = rabbitConnectionFactory.newConnection();
                log.info("RabbitMQ connection established");
                return connection;
            } catch (IOException | TimeoutException e) {
                if (i == maxRetries) {
                    log.error("Failed to connect to RabbitMQ after {} attempts", maxRetries);
                    throw e;
                }
                log.warn("RabbitMQ connection attempt {}/{} failed, retrying in 3s...", i, maxRetries);
                Thread.sleep(3000);
            }
        }
        throw new IOException("Failed to connect to RabbitMQ");
    }

    @Bean
    public Channel rabbitChannel(Connection rabbitConnection) throws IOException {
        Channel channel = rabbitConnection.createChannel();

        // Declare a direct exchange
        channel.exchangeDeclare(EXCHANGE_NAME, "direct", true);

        // Declare a durable queue
        channel.queueDeclare(RECOMMENDATION_QUEUE, true, false, false, null);

        // Bind queue to exchange with routing key
        channel.queueBind(RECOMMENDATION_QUEUE, EXCHANGE_NAME, RECOMMENDATION_ROUTING_KEY);

        log.info("RabbitMQ exchange '{}', queue '{}' declared and bound", EXCHANGE_NAME, RECOMMENDATION_QUEUE);
        return channel;
    }
}
