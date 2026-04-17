package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.co.fuelfinder.persistence.repository.StationQueryRepository;
import uk.co.fuelfinder.persistence.repository.projection.NearbyStationProjection;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CachedStationQueryServiceCachingTest {

    @Autowired
    private CachedStationQueryService cachedStationQueryService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private StationQueryRepository stationQueryRepository;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            if (cacheManager.getCache(name) != null) {
                cacheManager.getCache(name).clear();
            }
        });
    }

    @Test
    void reusesCacheEntryForRepeatedNearbyQueries() {
        when(stationQueryRepository.findNearbyStations(51.5074, -0.1278, 2000L, "E5", 10))
                .thenReturn(List.of(projection()));

        NormalizedStationQuery query = new NormalizedStationQuery(51.5074, -0.1278, 2000L, "E5", 10);

        cachedStationQueryService.findNearbyStations(query);
        cachedStationQueryService.findNearbyStations(query);

        verify(stationQueryRepository, times(1)).findNearbyStations(51.5074, -0.1278, 2000L, "E5", 10);
    }

    @Test
    void reusesCacheEntryForRepeatedCheapestNearbyQueries() {
        when(stationQueryRepository.findCheapestNearbyStations(51.5074, -0.1278, 2000L, "E5", 10))
                .thenReturn(List.of(projection()));

        NormalizedStationQuery query = new NormalizedStationQuery(51.5074, -0.1278, 2000L, "E5", 10);

        cachedStationQueryService.findCheapestNearbyStations(query);
        cachedStationQueryService.findCheapestNearbyStations(query);

        verify(stationQueryRepository, times(1)).findCheapestNearbyStations(51.5074, -0.1278, 2000L, "E5", 10);
    }

    @Test
    void usesDifferentCacheEntriesForDifferentNormalizedQueries() {
        when(stationQueryRepository.findNearbyStations(51.5074, -0.1278, 2000L, "E5", 10))
                .thenReturn(List.of(projection()));
        when(stationQueryRepository.findNearbyStations(51.5075, -0.1278, 2000L, "E5", 10))
                .thenReturn(List.of(projection()));

        cachedStationQueryService.findNearbyStations(new NormalizedStationQuery(51.5074, -0.1278, 2000L, "E5", 10));
        cachedStationQueryService.findNearbyStations(new NormalizedStationQuery(51.5075, -0.1278, 2000L, "E5", 10));

        verify(stationQueryRepository).findNearbyStations(51.5074, -0.1278, 2000L, "E5", 10);
        verify(stationQueryRepository).findNearbyStations(51.5075, -0.1278, 2000L, "E5", 10);
    }

    private static NearbyStationProjection projection() {
        UUID stationId = UUID.randomUUID();
        return new NearbyStationProjection() {
            @Override
            public UUID getStationId() {
                return stationId;
            }

            @Override
            public String getSiteId() {
                return "site-1";
            }

            @Override
            public String getBrand() {
                return "Shell";
            }

            @Override
            public String getAddress() {
                return "221B Baker Street";
            }

            @Override
            public String getCity() {
                return "London";
            }

            @Override
            public String getCounty() {
                return "Greater London";
            }

            @Override
            public String getCountry() {
                return "UK";
            }

            @Override
            public String getPostcode() {
                return "NW1 6XE";
            }

            @Override
            public String getFuelType() {
                return "E5";
            }

            @Override
            public Integer getPricePence() {
                return 145;
            }

            @Override
            public Double getDistanceMeters() {
                return 321.5;
            }
        };
    }
}
