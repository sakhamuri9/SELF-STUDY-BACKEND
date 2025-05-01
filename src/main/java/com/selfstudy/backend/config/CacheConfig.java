package com.selfstudy.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${voice.narration.cache.max-size:100}")
    private int cacheMaxSize;

    @Value("${voice.narration.cache.expire-after-write:24h}")
    private String cacheExpireAfterWrite;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("audioFiles");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(parseDuration(cacheExpireAfterWrite))
                .recordStats();
    }

    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return Duration.ofHours(24); // Default to 24 hours
        }

        String value = durationStr.replaceAll("[^\\d]", "");
        String unit = durationStr.replaceAll("[\\d]", "").toLowerCase();

        int amount = Integer.parseInt(value);
        
        switch (unit) {
            case "s":
                return Duration.ofSeconds(amount);
            case "m":
                return Duration.ofMinutes(amount);
            case "h":
                return Duration.ofHours(amount);
            case "d":
                return Duration.ofDays(amount);
            default:
                return Duration.ofHours(24); // Default to 24 hours
        }
    }
}
