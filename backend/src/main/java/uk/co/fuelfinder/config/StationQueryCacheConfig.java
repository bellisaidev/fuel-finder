package uk.co.fuelfinder.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class StationQueryCacheConfig {

    public static final String NEARBY_STATIONS_CACHE = "nearbyStations";
    public static final String CHEAPEST_NEARBY_STATIONS_CACHE = "cheapestNearbyStations";
    public static final String STATION_DETAILS_CACHE = "stationDetails";
    public static final String STATION_PRICE_HISTORY_CACHE = "stationPriceHistory";
    public static final String STATION_PRICE_HISTORY_SUMMARY_CACHE = "stationPriceHistorySummary";

    @Bean
    public CacheManager cacheManager(StationQueryCacheProperties properties) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache(NEARBY_STATIONS_CACHE, properties.nearby()),
                buildCache(CHEAPEST_NEARBY_STATIONS_CACHE, properties.cheapestNearby()),
                buildCache(STATION_DETAILS_CACHE, properties.details()),
                buildCache(STATION_PRICE_HISTORY_CACHE, properties.history()),
                buildCache(STATION_PRICE_HISTORY_SUMMARY_CACHE, properties.historySummary())
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String cacheName, StationQueryCacheProperties.CacheSpec spec) {
        return new CaffeineCache(cacheName, Caffeine.newBuilder()
                .expireAfterWrite(spec.ttl())
                .maximumSize(spec.maxSize())
                .build());
    }
}
