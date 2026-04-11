package uk.co.fuelfinder.ingestion.raw.writer;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JdbcRepositoriesIT {

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
    private LatestPriceJdbcRepository latestPriceJdbcRepository;

    @Autowired
    private PriceObservationJdbcRepository priceObservationJdbcRepository;

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
                "site-jdbc-1",
                "Shell",
                true
        );
    }

    @Test
    void insertIfNotExistsInsertsRowAndDedupesDuplicateSourceHash() {
        UUID observationId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-04-11T10:15:30Z");

        int firstInsert = priceObservationJdbcRepository.insertIfNotExists(
                observationId,
                stationId,
                "E10",
                146,
                observedAt,
                "hash-1"
        );
        int duplicateInsert = priceObservationJdbcRepository.insertIfNotExists(
                UUID.randomUUID(),
                stationId,
                "E10",
                146,
                observedAt.plusSeconds(60),
                "hash-1"
        );

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM price_observation", Integer.class)).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT station_id, fuel_type, price_pence, source_hash
                FROM price_observation
                WHERE id = ?
                """,
                observationId
        );

        assertThat(row.get("station_id")).isEqualTo(stationId);
        assertThat(row.get("fuel_type")).isEqualTo("E10");
        assertThat(row.get("price_pence")).isEqualTo(146);
        assertThat(row.get("source_hash")).isEqualTo("hash-1");
    }

    @Test
    void upsertInsertsThenUpdatesLatestPrice() {
        Instant initialObservedAt = Instant.parse("2026-04-11T10:15:30Z");
        Instant updatedObservedAt = Instant.parse("2026-04-11T11:15:30Z");

        int inserted = latestPriceJdbcRepository.upsert(stationId, "E10", 146, initialObservedAt);
        int updated = latestPriceJdbcRepository.upsert(stationId, "E10", 151, updatedObservedAt);

        assertThat(inserted).isEqualTo(1);
        assertThat(updated).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM latest_price", Integer.class)).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT station_id, fuel_type, price_pence, currency, observed_at
                FROM latest_price
                WHERE station_id = ? AND fuel_type = ?
                """,
                stationId,
                "E10"
        );

        assertThat(row.get("station_id")).isEqualTo(stationId);
        assertThat(row.get("fuel_type")).isEqualTo("E10");
        assertThat(row.get("price_pence")).isEqualTo(151);
        assertThat(row.get("currency")).isEqualTo("GBP");
        assertThat(toOffsetDateTime(row.get("observed_at"))).isEqualTo(updatedObservedAt.atOffset(ZoneOffset.UTC));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unexpected timestamp type: " + value);
    }
}
