package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StationPriceHistoryRepositoryIT {

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

    private UUID activeStationId;
    private UUID inactiveStationId;

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

        activeStationId = UUID.randomUUID();
        inactiveStationId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO station (id, retailer_id, site_id, brand, is_active)
                VALUES (?, ?, ?, ?, ?)
                """,
                activeStationId,
                retailerId,
                "site-active",
                "Shell",
                true
        );

        jdbcTemplate.update(
                """
                INSERT INTO station (id, retailer_id, site_id, brand, is_active)
                VALUES (?, ?, ?, ?, ?)
                """,
                inactiveStationId,
                retailerId,
                "site-inactive",
                "BP",
                false
        );

        insertObservation(activeStationId, "E5", 145, "2026-04-18T10:15:30Z");
        insertObservation(activeStationId, "E5", 149, "2026-04-18T08:00:00Z");
        insertObservation(activeStationId, "E5", 151, "2026-04-17T23:59:59Z");
        insertObservation(activeStationId, "B7", 155, "2026-04-18T10:20:30Z");
        insertObservation(inactiveStationId, "E5", 159, "2026-04-18T07:00:00Z");
    }

    @Test
    void findsStationPriceHistoryNewestFirstWithInclusiveTimeFilters() {
        List<PriceObservationEntity> observations = findStationPriceHistory(
                activeStationId,
                "E5",
                OffsetDateTime.parse("2026-04-18T00:00:00Z"),
                OffsetDateTime.parse("2026-04-18T10:15:30Z"),
                PageRequest.of(0, 10)
        );

        assertThat(observations).extracting(PriceObservationEntity::getPricePence)
                .containsExactly(145, 149);
        assertThat(observations).extracting(PriceObservationEntity::getObservedAt)
                .containsExactly(
                        OffsetDateTime.parse("2026-04-18T10:15:30Z"),
                        OffsetDateTime.parse("2026-04-18T08:00:00Z")
                );
    }

    @Test
    void findsHistoryForExistingInactiveStation() {
        List<PriceObservationEntity> observations = findStationPriceHistory(
                inactiveStationId,
                "E5",
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(observations).hasSize(1);
        assertThat(observations.getFirst().getPricePence()).isEqualTo(159);
    }

    @Test
    void returnsEmptyWhenNoObservationsMatchFilter() {
        List<PriceObservationEntity> observations = findStationPriceHistory(
                activeStationId,
                "E5",
                OffsetDateTime.parse("2026-04-19T00:00:00Z"),
                null,
                PageRequest.of(0, 10)
        );

        assertThat(observations).isEmpty();
    }

    @Test
    void filtersUsingToOnlyInclusiveBound() {
        List<PriceObservationEntity> observations = findStationPriceHistory(
                activeStationId,
                "E5",
                null,
                OffsetDateTime.parse("2026-04-18T08:00:00Z"),
                PageRequest.of(0, 10)
        );

        assertThat(observations).extracting(PriceObservationEntity::getPricePence)
                .containsExactly(149, 151);
    }

    @Test
    void ordersDeterministicallyByIdWhenObservedAtTimestampsTie() {
        UUID tieStationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO station (id, retailer_id, site_id, brand, is_active)
                VALUES (?, (SELECT id FROM retailer WHERE name = ?), ?, ?, ?)
                """,
                tieStationId,
                SEEDED_RETAILER_NAME,
                "site-tie",
                "Esso",
                true
        );

        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        OffsetDateTime tiedObservedAt = OffsetDateTime.parse("2026-04-18T10:15:30Z");

        insertObservation(tieStationId, "E5", 140, tiedObservedAt, lowerId);
        insertObservation(tieStationId, "E5", 141, tiedObservedAt, higherId);

        List<PriceObservationEntity> observations = findStationPriceHistory(
                tieStationId,
                "E5",
                null,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(observations).extracting(PriceObservationEntity::getId)
                .startsWith(higherId, lowerId);
        assertThat(observations).extracting(PriceObservationEntity::getPricePence)
                .startsWith(141, 140);
    }

    private void insertObservation(UUID stationId, String fuelType, int pricePence, String observedAt) {
        insertObservation(stationId, fuelType, pricePence, OffsetDateTime.parse(observedAt), UUID.randomUUID());
    }

    private void insertObservation(
            UUID stationId,
            String fuelType,
            int pricePence,
            OffsetDateTime observedAt,
            UUID observationId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO price_observation (id, station_id, fuel_type, price_pence, currency, observed_at, source_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                observationId,
                stationId,
                fuelType,
                pricePence,
                "GBP",
                observedAt,
                UUID.randomUUID().toString()
        );
    }

    private List<PriceObservationEntity> findStationPriceHistory(
            UUID stationId,
            String fuelType,
            OffsetDateTime from,
            OffsetDateTime to,
            PageRequest pageRequest
    ) {
        if (from != null && to != null) {
            return priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                    stationId,
                    fuelType,
                    from,
                    to,
                    pageRequest
            );
        }

        if (from != null) {
            return priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
                    stationId,
                    fuelType,
                    from,
                    pageRequest
            );
        }

        if (to != null) {
            return priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
                    stationId,
                    fuelType,
                    to,
                    pageRequest
            );
        }

        return priceObservationRepository.findByStationIdAndFuelTypeOrderByObservedAtDescIdDesc(
                stationId,
                fuelType,
                pageRequest
        );
    }
}
