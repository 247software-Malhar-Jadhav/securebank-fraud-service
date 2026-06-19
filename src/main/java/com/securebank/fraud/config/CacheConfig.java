package com.securebank.fraud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Configures a Redis-backed {@link RedisCacheManager} for the "assistant" cache.
 *
 * <p>There is no relational database in this service; Redis is the only stateful store.
 * Here it is used purely as a cache (assistant answers). Entries are JSON-serialised so
 * the cached {@code AiOrchestrator.Result} survives round-trips and is human-inspectable
 * in Redis.</p>
 */
@Configuration
public class CacheConfig {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                // 10-minute TTL: assistant answers are stable enough to cache briefly,
                // short enough that config/locale changes are reflected quickly.
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .build();
    }
}
