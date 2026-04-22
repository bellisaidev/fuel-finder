package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.projection.StationPriceHistorySummaryBucketProjection;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StationPriceHistorySummaryRepositoryIT {

    private static final String SEEDED_RETAILER_NAME = "FUEL_FINDER_API";

    @Container
    static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGIS::getUsername);
        registry.add("spring.datasource.password", POSTGIS::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PriceObservationRepository priceObservationRepository;

    private UUID stationId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM latest_price");
        jdbcTemplate.update("DELETE FROM price_observation");
        jdbcTemplate.update("DELETE FROM station");

        UUID retailerId = jdbcTemplate.queryForObject(
                "SELECT id FROM retailer WHERE name = ?",
                UUID.class,
                SEEDED_RETAILER_NAME
        );

        stationId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO station (id, retailer_id, site_id, brand, is_active)
                VALUES (?, ?, ?, ?, ?)
                """,
                stationId,
                retailerId,
                "site-summary",
                "Shell",
                true
        );

        insertObservation(stationId, "E5", 149, OffsetDateTime.parse("2026-04-18T08:00:00Z"), UUID.fromString("00000000-0000-0000-0000-000000000010"));
        insertObservation(stationId, "E5", 151, OffsetDateTime.parse("2026-04-18T10:00:00Z"), UUID.randomUUID());
        insertObservation(stationId, "E5", 145, OffsetDateTime.parse("2026-04-18T12:00:00Z"), UUID.randomUUID());
        insertObservation(stationId, "E5", 142, OffsetDateTime.parse("2026-04-17T09:00:00Z"), UUID.randomUUID());
        insertObservation(stationId, "E5", 140, OffsetDateTime.parse("2026-04-17T20:00:00Z"), UUID.randomUUID());
        insertObservation(stationId, "B7", 160, OffsetDateTime.parse("2026-04-18T10:00:00Z"), UUID.randomUUID());
    }

    @Test
    void summarizesDailyBucketsInUtcOrderedNewestFirst() {
        List<StationPriceHistorySummaryBucketProjection> buckets =
                priceObservationRepository.findDailySummaryByStationIdAndFuelType(stationId, "E5", null, null, 10);

        assertThat(buckets).hasSize(2);
        assertThat(buckets).extracting(StationPriceHistorySummaryBucketProjection::getBucketStart)
                .containsExactly(
                        Instant.parse("2026-04-18T00:00:00Z"),
                        Instant.parse("2026-04-17T00:00:00Z")
                );

        StationPriceHistorySummaryBucketProjection newest = buckets.getFirst();
        assertThat(newest.getFirstPricePence()).isEqualTo(149);
        assertThat(newest.getHighestPricePence()).isEqualTo(151);
        assertThat(newest.getLowestPricePence()).isEqualTo(145);
        assertThat(newest.getLastPricePence()).isEqualTo(145);
        assertThat(newest.getObservationCount()).isEqualTo(3L);
    }

    @Test
    void appliesInclusiveFromAndToFiltersBeforeSummarizing() {
        List<StationPriceHistorySummaryBucketProjection> buckets = priceObservationRepository.findDailySummaryByStationIdAndFuelType(
                stationId,
                "E5",
                OffsetDateTime.parse("2026-04-18T08:00:00Z"),
                OffsetDateTime.parse("2026-04-18T12:00:00Z"),
                10
        );

        assertThat(buckets).hasSize(1);
        assertThat(buckets.getFirst().getFirstPricePence()).isEqualTo(149);
        assertThat(buckets.getFirst().getLastPricePence()).isEqualTo(145);
        assertThat(buckets.getFirst().getObservationCount()).isEqualTo(3L);
    }

    @Test
    void usesDeterministicTieBreakersForFirstAndLastWithinBucket() {
        OffsetDateTime tiedObservedAt = OffsetDateTime.parse("2026-04-16T10:15:30Z");
        insertObservation(stationId, "E5", 139, tiedObservedAt, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        insertObservation(stationId, "E5", 141, tiedObservedAt, UUID.fromString("00000000-0000-0000-0000-000000000002"));

        List<StationPriceHistorySummaryBucketProjection> buckets = priceObservationRepository.findDailySummaryByStationIdAndFuelType(
                stationId,
                "E5",
                OffsetDateTime.parse("2026-04-16T00:00:00Z"),
                OffsetDateTime.parse("2026-04-16T23:59:59Z"),
                10
        );

        assertThat(buckets).hasSize(1);
        assertThat(buckets.getFirst().getFirstPricePence()).isEqualTo(139);
        assertThat(buckets.getFirst().getLastPricePence()).isEqualTo(141);
    }

    @Test
    void returnsEmptyWhenNoObservationsMatchSummaryFilter() {
        List<StationPriceHistorySummaryBucketProjection> buckets = priceObservationRepository.findDailySummaryByStationIdAndFuelType(
                stationId,
                "E5",
                OffsetDateTime.parse("2026-04-20T00:00:00Z"),
                null,
                10
        );

        assertThat(buckets).isEmpty();
    }

    private void insertObservation(UUID stationId, String fuelType, int pricePence, OffsetDateTime observedAt, UUID id) {
        jdbcTemplate.update(
                """
                INSERT INTO price_observation (id, station_id, fuel_type, price_pence, currency, observed_at, source_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                stationId,
                fuelType,
                pricePence,
                "GBP",
                observedAt,
                UUID.randomUUID().toString()
        );
    }
}
