package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static uk.co.fuelfinder.config.StationQueryCacheConfig.CHEAPEST_NEARBY_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.NEARBY_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_DETAILS_CACHE;

@Component
@RequiredArgsConstructor
public class StationQueryCacheInvalidationListener {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLatestPricesChanged(LatestPricesChangedEvent ignored) {
        clearCache(NEARBY_STATIONS_CACHE);
        clearCache(CHEAPEST_NEARBY_STATIONS_CACHE);
        clearCache(STATION_DETAILS_CACHE);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStationsChanged(StationsChangedEvent ignored) {
        clearCache(STATION_DETAILS_CACHE);
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
