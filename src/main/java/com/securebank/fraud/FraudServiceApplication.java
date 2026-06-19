package com.securebank.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Entry point for the SecureBank fraud-service.
 *
 * <p>This service is the platform's "AI brain": it exposes a gRPC {@code FraudService}
 * (Score / Ask / Insights) and a small optional REST surface. There is no relational
 * database; Redis is used for caching (e.g. assistant answers).</p>
 *
 * <p>Virtual threads are enabled via {@code spring.threads.virtual.enabled=true} in
 * application.yml so blocking work (the LLM HTTP call, Redis I/O) scales cheaply on
 * Java 21 without a large platform-thread pool.</p>
 *
 * <p>{@link EnableCaching} turns on Spring's cache abstraction so the assistant can cache
 * deterministic answers in Redis.</p>
 */
@SpringBootApplication
@EnableCaching
public class FraudServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudServiceApplication.class, args);
    }
}
