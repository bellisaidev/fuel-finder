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
import uk.co.fuelfinder.persistence.repository.StationQueryRepository;
import uk.co.fuelfinder.persistence.repository.projection.StationMapMarkerProjection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StationInBoundsRepositoryIT {

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
    private StationQueryRepository stationQueryRepository;

    private UUID insideHighPriceId;
    private UUID westEdgeId;
    private UUID northEastCornerId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM latest_price");
        jdbcTemplate.update("DELETE FROM station");

        UUID retailerId = jdbcTemplate.queryForObject(
                "SELECT id FROM retailer WHERE name = ?",
                UUID.class,
                SEEDED_RETAILER_NAME
        );

        insideHighPriceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        westEdgeId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        northEastCornerId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        insertStation(retailerId, insideHighPriceId, "inside-high", "Shell", "NW1 6XE", -0.10, 51.50, true);
        insertStation(retailerId, westEdgeId, "west-edge", "BP", "W1A 1AA", -0.20, 51.50, true);
        insertStation(retailerId, northEastCornerId, "north-east-corner", "Esso", "EC1A 1BB", -0.05, 51.55, true);
        insertStation(retailerId, UUID.fromString("00000000-0000-0000-0000-000000000004"), "outside", "Texaco", "SW1A 1AA", -0.30, 51.50, true);
        insertStation(retailerId, UUID.fromString("00000000-0000-0000-0000-000000000005"), "inactive", "Gulf", "SE1 1AA", -0.12, 51.51, false);
        insertStationWithoutLocation(retailerId, UUID.fromString("00000000-0000-0000-0000-000000000006"), "null-location", "Murco", "N1 1AA", true);
        insertStation(retailerId, UUID.fromString("00000000-0000-0000-0000-000000000007"), "wrong-fuel", "Jet", "E1 1AA", -0.11, 51.52, true);

        insertLatestPrice(insideHighPriceId, "E5", 160);
        insertLatestPrice(westEdgeId, "E5", 140);
        insertLatestPrice(northEastCornerId, "E5", 150);
        insertLatestPrice(UUID.fromString("00000000-0000-0000-0000-000000000004"), "E5", 130);
        insertLatestPrice(UUID.fromString("00000000-0000-0000-0000-000000000005"), "E5", 120);
        insertLatestPrice(UUID.fromString("00000000-0000-0000-0000-000000000006"), "E5", 110);
        insertLatestPrice(UUID.fromString("00000000-0000-0000-0000-000000000007"), "B7", 100);
    }

    @Test
    void findsActiveStationsInsideInclusiveBoundsOrderedByStationId() {
        List<StationMapMarkerProjection> markers = stationQueryRepository.findStationsInBounds(
                -0.20,
                51.45,
                -0.05,
                51.55,
                "E5",
                10
        );

        assertThat(markers).extracting(StationMapMarkerProjection::getStationId)
                .containsExactly(insideHighPriceId, westEdgeId, northEastCornerId);
        assertThat(markers).extracting(StationMapMarkerProjection::getPricePence)
                .containsExactly(160, 140, 150);

        StationMapMarkerProjection first = markers.getFirst();
        assertThat(first.getSiteId()).isEqualTo("inside-high");
        assertThat(first.getBrand()).isEqualTo("Shell");
        assertThat(first.getPostcode()).isEqualTo("NW1 6XE");
        assertThat(first.getLatitude()).isEqualTo(51.50);
        assertThat(first.getLongitude()).isEqualTo(-0.10);
        assertThat(first.getFuelType()).isEqualTo("E5");
    }

    @Test
    void respectsLimit() {
        List<StationMapMarkerProjection> markers = stationQueryRepository.findStationsInBounds(
                -0.20,
                51.45,
                -0.05,
                51.55,
                "E5",
                2
        );

        assertThat(markers).extracting(StationMapMarkerProjection::getStationId)
                .containsExactly(insideHighPriceId, westEdgeId);
    }

    private void insertStation(
            UUID retailerId,
            UUID stationId,
            String siteId,
            String brand,
            String postcode,
            double longitude,
            double latitude,
            boolean active
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO station (id, retailer_id, site_id, brand, postcode, location, is_active)
                VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
                """,
                stationId,
                retailerId,
                siteId,
                brand,
                postcode,
                longitude,
                latitude,
                active
        );
    }

    private void insertStationWithoutLocation(
            UUID retailerId,
            UUID stationId,
            String siteId,
            String brand,
            String postcode,
            boolean active
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO station (id, retailer_id, site_id, brand, postcode, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                stationId,
                retailerId,
                siteId,
                brand,
                postcode,
                active
        );
    }

    private void insertLatestPrice(UUID stationId, String fuelType, int pricePence) {
        jdbcTemplate.update(
                """
                INSERT INTO latest_price (station_id, fuel_type, price_pence, currency, observed_at, reported_updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                stationId,
                fuelType,
                pricePence,
                "GBP",
                OffsetDateTime.parse("2026-04-18T10:15:30Z"),
                OffsetDateTime.parse("2026-04-18T10:10:00Z")
        );
    }
}
