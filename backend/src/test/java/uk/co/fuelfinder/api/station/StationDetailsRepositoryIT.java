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
import uk.co.fuelfinder.persistence.entity.LatestPriceEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.LatestPriceRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StationDetailsRepositoryIT {

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
    private StationRepository stationRepository;

    @Autowired
    private LatestPriceRepository latestPriceRepository;

    private UUID activeStationId;
    private UUID inactiveStationId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM latest_price");
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
                INSERT INTO station (id, retailer_id, site_id, brand, address, city, county, country, postcode, location, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                """,
                activeStationId,
                retailerId,
                "site-active",
                "Shell",
                "221B Baker Street",
                "London",
                "Greater London",
                "UK",
                "NW1 6XE",
                -0.1585,
                51.5237,
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

        jdbcTemplate.update(
                """
                INSERT INTO latest_price (station_id, fuel_type, price_pence, currency, observed_at, reported_updated_at)
                VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """,
                activeStationId,
                "E10",
                147,
                "GBP",
                OffsetDateTime.parse("2026-04-18T10:15:30Z"),
                OffsetDateTime.parse("2026-04-18T10:10:00Z"),
                activeStationId,
                "B7",
                155,
                "GBP",
                OffsetDateTime.parse("2026-04-18T10:20:30Z"),
                OffsetDateTime.parse("2026-04-18T10:18:00Z")
        );
    }

    @Test
    void findsActiveStationWithLocationFields() {
        StationEntity station = stationRepository.findByIdAndActiveTrue(activeStationId).orElseThrow();

        assertThat(station.getAddress()).isEqualTo("221B Baker Street");
        assertThat(station.getCity()).isEqualTo("London");
        assertThat(station.getCounty()).isEqualTo("Greater London");
        assertThat(station.getCountry()).isEqualTo("UK");
        assertThat(station.getPostcode()).isEqualTo("NW1 6XE");
        assertThat(station.getLocation()).isNotNull();
        assertThat(station.getLocation().getY()).isEqualTo(51.5237);
        assertThat(station.getLocation().getX()).isEqualTo(-0.1585);
    }

    @Test
    void findsAllLatestPricesForStationOrderedByFuelType() {
        List<LatestPriceEntity> latestPrices = latestPriceRepository.findByStationId(activeStationId);

        assertThat(latestPrices).hasSize(2);
        assertThat(latestPrices).extracting(entity -> entity.getId().getFuelType())
                .containsExactly("B7", "E10");
        assertThat(latestPrices).extracting(LatestPriceEntity::getObservedAt)
                .containsExactly(
                        OffsetDateTime.parse("2026-04-18T10:20:30Z"),
                        OffsetDateTime.parse("2026-04-18T10:15:30Z")
                );
    }

    @Test
    void doesNotReturnInactiveStationFromPublicLookup() {
        assertThat(stationRepository.findByIdAndActiveTrue(inactiveStationId)).isEmpty();
    }
}
