package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.CHEAPEST_NEARBY_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.NEARBY_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_DETAILS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_PRICE_HISTORY_CACHE;

@SpringBootTest
@ActiveProfiles("test")
class StationQueryCacheInvalidationIntegrationTest {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetCaches() {
        clearCache(NEARBY_STATIONS_CACHE);
        clearCache(CHEAPEST_NEARBY_STATIONS_CACHE);
        clearCache(STATION_DETAILS_CACHE);
        clearCache(STATION_PRICE_HISTORY_CACHE);
    }

    @Test
    void clearsCachesAfterCommit() {
        putValue(NEARBY_STATIONS_CACHE, "k1", "v1");
        putValue(CHEAPEST_NEARBY_STATIONS_CACHE, "k2", "v2");
        putValue(STATION_DETAILS_CACHE, "k3", "v3");

        transactionTemplate.executeWithoutResult(status ->
                applicationEventPublisher.publishEvent(new LatestPricesChangedEvent("test"))
        );

        assertEquals(null, getValue(NEARBY_STATIONS_CACHE, "k1"));
        assertEquals(null, getValue(CHEAPEST_NEARBY_STATIONS_CACHE, "k2"));
        assertEquals(null, getValue(STATION_DETAILS_CACHE, "k3"));
    }

    @Test
    void clearsStationPriceHistoryCacheAfterPriceObservationChangeCommit() {
        putValue(STATION_PRICE_HISTORY_CACHE, "k4", "v4");

        transactionTemplate.executeWithoutResult(status ->
                applicationEventPublisher.publishEvent(new PriceObservationsChangedEvent("test"))
        );

        assertEquals(null, getValue(STATION_PRICE_HISTORY_CACHE, "k4"));
    }

    @Test
    void clearsStationDetailsCacheAfterStationChangeCommit() {
        putValue(STATION_DETAILS_CACHE, "k3", "v3");

        transactionTemplate.executeWithoutResult(status ->
                applicationEventPublisher.publishEvent(new StationsChangedEvent("test"))
        );

        assertEquals(null, getValue(STATION_DETAILS_CACHE, "k3"));
    }

    @Test
    void doesNotClearCachesWhenTransactionRollsBack() {
        putValue(NEARBY_STATIONS_CACHE, "k1", "v1");
        putValue(CHEAPEST_NEARBY_STATIONS_CACHE, "k2", "v2");
        putValue(STATION_DETAILS_CACHE, "k3", "v3");
        putValue(STATION_PRICE_HISTORY_CACHE, "k4", "v4");

        transactionTemplate.executeWithoutResult(status -> {
            applicationEventPublisher.publishEvent(new LatestPricesChangedEvent("test"));
            applicationEventPublisher.publishEvent(new PriceObservationsChangedEvent("test"));
            applicationEventPublisher.publishEvent(new StationsChangedEvent("test"));
            status.setRollbackOnly();
        });

        assertEquals("v1", getValue(NEARBY_STATIONS_CACHE, "k1"));
        assertEquals("v2", getValue(CHEAPEST_NEARBY_STATIONS_CACHE, "k2"));
        assertEquals("v3", getValue(STATION_DETAILS_CACHE, "k3"));
        assertEquals("v4", getValue(STATION_PRICE_HISTORY_CACHE, "k4"));
    }

    @TestConfiguration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        @Primary
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private void putValue(String cacheName, String key, String value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private Object getValue(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper value = cache.get(key);
        return value == null ? null : value.get();
    }
}
