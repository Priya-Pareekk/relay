package com.relay;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("relay_test_db")
            .withUsername("test_user")
            .withPassword("test_password");

    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("relay.retry.poller-interval-ms", () -> "1000");
        registry.add("relay.retry.base-delay-seconds", () -> "1");
        registry.add("relay.cron.eval-interval-ms", () -> "1000");
        registry.add("relay.security.api-key", () -> "relay-secret-api-key");
    }
}

