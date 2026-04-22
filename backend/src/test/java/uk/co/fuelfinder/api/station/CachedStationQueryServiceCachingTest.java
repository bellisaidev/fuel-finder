package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.co.fuelfinder.api.StationNotFoundException;
import uk.co.fuelfinder.persistence.entity.LatestPriceEntity;
import uk.co.fuelfinder.persistence.entity.LatestPriceId;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.LatestPriceRepository;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;
import uk.co.fuelfinder.persistence.repository.StationQueryRepository;
import uk.co.fuelfinder.persistence.repository.projection.NearbyStationProjection;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_DETAILS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_PRICE_HISTORY_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_PRICE_HISTORY_SUMMARY_CACHE;

@SpringBootTest
@ActiveProfiles("test")
class CachedStationQueryServiceCachingTest {

    @Autowired
    private CachedStationQueryService cachedStationQueryService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private StationQueryRepository stationQueryRepository;

    @MockitoBean
    private StationRepository stationRepository;

    @MockitoBean
    private LatestPriceRepository latestPriceRepository;

    @MockitoBean
    private PriceObservationRepository priceObservationRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory();

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

    @Test
    void reusesCacheEntryForRepeatedStationDetailsLookups() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        when(stationRepository.findByIdAndActiveTrue(stationId)).thenReturn(Optional.of(station));
        when(latestPriceRepository.findByStationId(stationId)).thenReturn(List.of(latestPrice(station, "E5", 145)));

        cachedStationQueryService.getStationDetails(stationId);
        cachedStationQueryService.getStationDetails(stationId);

        verify(stationRepository, times(1)).findByIdAndActiveTrue(stationId);
        verify(latestPriceRepository, times(1)).findByStationId(stationId);
    }

    @Test
    void usesDifferentCacheEntriesForDifferentStationDetailsLookups() {
        UUID firstStationId = UUID.randomUUID();
        UUID secondStationId = UUID.randomUUID();
        StationEntity firstStation = station(firstStationId, "SITE-1");
        StationEntity secondStation = station(secondStationId, "SITE-2");

        when(stationRepository.findByIdAndActiveTrue(firstStationId)).thenReturn(Optional.of(firstStation));
        when(stationRepository.findByIdAndActiveTrue(secondStationId)).thenReturn(Optional.of(secondStation));
        when(latestPriceRepository.findByStationId(firstStationId)).thenReturn(List.of(latestPrice(firstStation, "E5", 145)));
        when(latestPriceRepository.findByStationId(secondStationId)).thenReturn(List.of(latestPrice(secondStation, "E10", 147)));

        cachedStationQueryService.getStationDetails(firstStationId);
        cachedStationQueryService.getStationDetails(secondStationId);

        verify(stationRepository).findByIdAndActiveTrue(firstStationId);
        verify(stationRepository).findByIdAndActiveTrue(secondStationId);
        verify(latestPriceRepository).findByStationId(firstStationId);
        verify(latestPriceRepository).findByStationId(secondStationId);
    }

    @Test
    void doesNotCacheStationNotFoundException() {
        UUID stationId = UUID.randomUUID();
        when(stationRepository.findByIdAndActiveTrue(stationId)).thenReturn(Optional.empty());

        assertThrows(StationNotFoundException.class, () -> cachedStationQueryService.getStationDetails(stationId));
        assertThrows(StationNotFoundException.class, () -> cachedStationQueryService.getStationDetails(stationId));

        verify(stationRepository, times(2)).findByIdAndActiveTrue(stationId);
    }

    @Test
    void queriesRepositoryAgainAfterStationDetailsCacheIsCleared() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        when(stationRepository.findByIdAndActiveTrue(stationId)).thenReturn(Optional.of(station));
        when(latestPriceRepository.findByStationId(stationId)).thenReturn(List.of(latestPrice(station, "E5", 145)));

        cachedStationQueryService.getStationDetails(stationId);
        assertNotNull(cacheManager.getCache(STATION_DETAILS_CACHE));
        cacheManager.getCache(STATION_DETAILS_CACHE).clear();
        cachedStationQueryService.getStationDetails(stationId);

        verify(stationRepository, times(2)).findByIdAndActiveTrue(stationId);
        verify(latestPriceRepository, times(2)).findByStationId(stationId);
    }

    @Test
    void reusesCacheEntryForRepeatedStationPriceHistoryLookups() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");
        NormalizedStationPriceHistoryQuery query = new NormalizedStationPriceHistoryQuery(
                stationId,
                "E5",
                from,
                to,
                100
        );

        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        ))
                .thenReturn(List.of(priceObservation(station, "E5", 145, OffsetDateTime.parse("2026-04-18T10:15:30Z"))));

        cachedStationQueryService.getStationPriceHistory(query);
        cachedStationQueryService.getStationPriceHistory(query);

        verify(stationRepository, times(1)).findById(stationId);
        verify(priceObservationRepository, times(1)).findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        );
    }

    @Test
    void usesDifferentCacheEntriesForDifferentStationPriceHistoryQueries() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");

        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        ))
                .thenReturn(List.of(priceObservation(station, "E5", 145, OffsetDateTime.parse("2026-04-18T10:15:30Z"))));
        when(priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("B7"),
                eq(from),
                eq(to),
                any()
        ))
                .thenReturn(List.of(priceObservation(station, "B7", 155, OffsetDateTime.parse("2026-04-18T10:20:30Z"))));

        cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(stationId, "E5", from, to, 100));
        cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(stationId, "B7", from, to, 100));

        verify(stationRepository, times(2)).findById(stationId);
        verify(priceObservationRepository).findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        );
        verify(priceObservationRepository).findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("B7"),
                eq(from),
                eq(to),
                any()
        );
    }

    @Test
    void queriesRepositoryAgainAfterStationPriceHistoryCacheIsCleared() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");
        NormalizedStationPriceHistoryQuery query = new NormalizedStationPriceHistoryQuery(
                stationId,
                "E5",
                from,
                to,
                100
        );

        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        ))
                .thenReturn(List.of(priceObservation(station, "E5", 145, OffsetDateTime.parse("2026-04-18T10:15:30Z"))));

        cachedStationQueryService.getStationPriceHistory(query);
        assertNotNull(cacheManager.getCache(STATION_PRICE_HISTORY_CACHE));
        cacheManager.getCache(STATION_PRICE_HISTORY_CACHE).clear();
        cachedStationQueryService.getStationPriceHistory(query);

        verify(stationRepository, times(2)).findById(stationId);
        verify(priceObservationRepository, times(2)).findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        );
    }

    @Test
    void usesDifferentCacheEntriesForDifferentStationPriceHistoryRangesAndLimits() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime laterFrom = OffsetDateTime.parse("2026-04-18T06:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");

        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        )).thenReturn(List.of(priceObservation(station, "E5", 145, OffsetDateTime.parse("2026-04-18T10:15:30Z"))));
        when(priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(laterFrom),
                eq(to),
                any()
        )).thenReturn(List.of(priceObservation(station, "E5", 144, OffsetDateTime.parse("2026-04-18T10:15:30Z"))));
        when(priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                any()
        )).thenReturn(List.of(priceObservation(station, "E5", 143, OffsetDateTime.parse("2026-04-18T10:15:30Z"))));

        cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(stationId, "E5", from, to, 100));
        cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(stationId, "E5", laterFrom, to, 100));
        cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(stationId, "E5", from, null, 100));
        cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(stationId, "E5", from, to, 50));

        verify(stationRepository, times(4)).findById(stationId);
        verify(priceObservationRepository, times(2)).findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                eq(to),
                any()
        );
        verify(priceObservationRepository).findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(laterFrom),
                eq(to),
                any()
        );
        verify(priceObservationRepository).findByStationIdAndFuelTypeAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
                eq(stationId),
                eq("E5"),
                eq(from),
                any()
        );
    }

    @Test
    void reusesCacheEntryForRepeatedStationPriceHistorySummaryLookups() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");
        NormalizedStationPriceHistorySummaryQuery query = new NormalizedStationPriceHistorySummaryQuery(
                stationId,
                "E5",
                from,
                to,
                30
        );

        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(priceObservationRepository.findDailySummaryByStationIdAndFuelType(
                stationId,
                "E5",
                from,
                to,
                30
        )).thenReturn(List.of(summaryBucket(
                OffsetDateTime.parse("2026-04-18T00:00:00Z"),
                OffsetDateTime.parse("2026-04-19T00:00:00Z"),
                149,
                151,
                145,
                145,
                3L
        )));

        cachedStationQueryService.getStationPriceHistorySummary(query);
        cachedStationQueryService.getStationPriceHistorySummary(query);

        verify(stationRepository, times(1)).findById(stationId);
        verify(priceObservationRepository, times(1)).findDailySummaryByStationIdAndFuelType(
                stationId,
                "E5",
                from,
                to,
                30
        );
    }

    @Test
    void queriesRepositoryAgainAfterStationPriceHistorySummaryCacheIsCleared() {
        UUID stationId = UUID.randomUUID();
        StationEntity station = station(stationId, "SITE-1");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");
        NormalizedStationPriceHistorySummaryQuery query = new NormalizedStationPriceHistorySummaryQuery(
                stationId,
                "E5",
                from,
                to,
                30
        );

        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));
        when(priceObservationRepository.findDailySummaryByStationIdAndFuelType(
                stationId,
                "E5",
                from,
                to,
                30
        )).thenReturn(List.of(summaryBucket(
                OffsetDateTime.parse("2026-04-18T00:00:00Z"),
                OffsetDateTime.parse("2026-04-19T00:00:00Z"),
                149,
                151,
                145,
                145,
                3L
        )));

        cachedStationQueryService.getStationPriceHistorySummary(query);
        assertNotNull(cacheManager.getCache(STATION_PRICE_HISTORY_SUMMARY_CACHE));
        cacheManager.getCache(STATION_PRICE_HISTORY_SUMMARY_CACHE).clear();
        cachedStationQueryService.getStationPriceHistorySummary(query);

        verify(stationRepository, times(2)).findById(stationId);
        verify(priceObservationRepository, times(2)).findDailySummaryByStationIdAndFuelType(
                stationId,
                "E5",
                from,
                to,
                30
        );
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

    private StationEntity station(UUID stationId, String siteId) {
        return StationEntity.builder()
                .id(stationId)
                .siteId(siteId)
                .brand("Shell")
                .address("221B Baker Street")
                .city("London")
                .county("Greater London")
                .country("UK")
                .postcode("NW1 6XE")
                .location(point(-0.1585, 51.5237))
                .active(true)
                .build();
    }

    private LatestPriceEntity latestPrice(StationEntity station, String fuelType, int pricePence) {
        return LatestPriceEntity.builder()
                .id(new LatestPriceId(station.getId(), fuelType))
                .station(station)
                .pricePence(pricePence)
                .currency("GBP")
                .observedAt(OffsetDateTime.parse("2026-04-18T10:15:30Z"))
                .reportedUpdatedAt(OffsetDateTime.parse("2026-04-18T10:10:00Z"))
                .build();
    }

    private PriceObservationEntity priceObservation(
            StationEntity station,
            String fuelType,
            int pricePence,
            OffsetDateTime observedAt
    ) {
        return PriceObservationEntity.builder()
                .id(UUID.randomUUID())
                .station(station)
                .fuelType(fuelType)
                .pricePence(pricePence)
                .currency("GBP")
                .observedAt(observedAt)
                .sourceHash(UUID.randomUUID().toString())
                .build();
    }

    private uk.co.fuelfinder.persistence.repository.projection.StationPriceHistorySummaryBucketProjection summaryBucket(
            OffsetDateTime bucketStart,
            OffsetDateTime bucketEnd,
            int firstPricePence,
            int highestPricePence,
            int lowestPricePence,
            int lastPricePence,
            long observationCount
    ) {
        return new uk.co.fuelfinder.persistence.repository.projection.StationPriceHistorySummaryBucketProjection() {
            @Override
            public Instant getBucketStart() {
                return bucketStart.toInstant();
            }

            @Override
            public Instant getBucketEnd() {
                return bucketEnd.toInstant();
            }

            @Override
            public Integer getFirstPricePence() {
                return firstPricePence;
            }

            @Override
            public Integer getHighestPricePence() {
                return highestPricePence;
            }

            @Override
            public Integer getLowestPricePence() {
                return lowestPricePence;
            }

            @Override
            public Integer getLastPricePence() {
                return lastPricePence;
            }

            @Override
            public Long getObservationCount() {
                return observationCount;
            }
        };
    }

    private Point point(double longitude, double latitude) {
        Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(4326);
        return point;
    }
}
