package uk.co.fuelfinder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "fuelfinder.cache")
public record StationQueryCacheProperties(
        CacheSpec nearby,
        CacheSpec cheapestNearby,
        CacheSpec details,
        CacheSpec history,
        CacheSpec historySummary
) {

    public record CacheSpec(
            Duration ttl,
            long maxSize
    ) {
    }
}
