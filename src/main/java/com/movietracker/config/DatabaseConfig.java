package com.movietracker.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// DB config for R2DBC + jOOQ
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private final String host = System.getenv().getOrDefault("DB_HOST", "localhost");
    private final int port = Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432"));
    private final String database = System.getenv().getOrDefault("DB_NAME", "movietracker");
    private final String username = System.getenv().getOrDefault("DB_USER", "movietracker");
    private final String password = System.getenv().getOrDefault("DB_PASSWORD", "movietracker");

    @Bean
    public ConnectionFactory connectionFactory() {
        log.info("Connecting to PostgreSQL at {}:{}/{}", host, port, database);

        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(host)
                        .port(port)
                        .database(database)
                        .username(username)
                        .password(password)
                        .build()
        );

        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
                .maxSize(10)
                .initialSize(2)
                .maxIdleTime(Duration.ofMinutes(5))
                .build();

        return new ConnectionPool(poolConfig);
    }

    @Bean
    public DSLContext dslContext(ConnectionFactory connectionFactory) {
        return DSL.using(connectionFactory, SQLDialect.POSTGRES);
    }
}
