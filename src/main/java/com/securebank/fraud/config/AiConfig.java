package com.securebank.fraud.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Wiring for the AI layer.
 *
 * <p>Provides a {@link RestClient} pre-configured to talk to the Anthropic-style endpoint.
 * RestClient (Spring 6.1+) is a synchronous, fluent HTTP client; because the application
 * runs on virtual threads, a blocking client is the simplest correct choice here.</p>
 *
 * <p>{@link EnableConfigurationProperties} binds {@link AiProperties} so the rest of the
 * code can inject it as a normal bean.</p>
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * The HTTP client used by {@code LlmAiProvider}. We only set the base URL and sane
     * timeouts here; per-request headers (the API key, anthropic-version, model) are added
     * at call time so a changed key/config is picked up without rebuilding the client.
     */
    @Bean
    RestClient anthropicRestClient(AiProperties props) {
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        // Keep timeouts tight: the circuit breaker should trip quickly rather than hang
        // a transfer that is waiting on Score()/Ask().
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
