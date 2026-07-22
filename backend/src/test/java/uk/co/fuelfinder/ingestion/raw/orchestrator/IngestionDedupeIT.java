package uk.co.fuelfinder.ingestion.raw.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import uk.co.fuelfinder.ingestion.raw.FeedType;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderFuelPricesClient;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderPfsClient;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPriceDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsLocationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.repository.LatestPriceRepository;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.RawFeedFetchRepository;
import uk.co.fuelfinder.persistence.repository.RetailerRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IngestionDedupeIT {

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
        registry.add("fuelfinder.api.oauth.client-id", () -> "test-client");
        registry.add("fuelfinder.api.oauth.client-secret", () -> "test-secret");
    }

    @Autowired
    private RetailerIngestionService retailerIngestionService;

    @Autowired
    private RetailerRepository retailerRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private PriceObservationRepository priceObservationRepository;

    @Autowired
    private LatestPriceRepository latestPriceRepository;

    @Autowired
    private RawFeedFetchRepository rawFeedFetchRepository;

    @MockitoBean
    private FuelFinderPfsClient pfsClient;

    @MockitoBean
    private FuelFinderFuelPricesClient fuelPricesClient;

    private RetailerEntity retailer;

    @BeforeEach
    void setUp() {
        latestPriceRepository.deleteAllInBatch();
        priceObservationRepository.deleteAllInBatch();
        stationRepository.deleteAllInBatch();
        rawFeedFetchRepository.deleteAllInBatch();

        retailer = retailerRepository.findByName(SEEDED_RETAILER_NAME)
                .orElseThrow(() -> new IllegalStateException("Seeded retailer not found: " + SEEDED_RETAILER_NAME));
    }

    @Test
    void ingestTwiceWithSamePayloadDoesNotCreateDuplicatePriceObservations() {
        when(pfsClient.fetchBatch(1)).thenReturn(List.of(
                pfsStation("site-1", "Shell", 51.5007, -0.1246)
        ));
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(
                fuelPricesStation("site-1", "E10", "1.459", "B7", "1.529")
        ));

        RawIngestionSummary first = retailerIngestionService.ingest(retailer);
        RawIngestionSummary second = retailerIngestionService.ingest(retailer);

        assertThat(first.isSuccess()).isTrue();
        assertThat(second.isSuccess()).isTrue();
        assertThat(stationRepository.count()).isEqualTo(1);
        assertThat(priceObservationRepository.count()).isEqualTo(2);
        assertThat(latestPriceRepository.count()).isEqualTo(2);
        assertThat(rawFeedFetchRepository.count()).isEqualTo(4);

        List<RawFeedFetchEntity> rawFeeds = rawFeedFetchRepository.findAll().stream()
                .sorted(Comparator.comparing(RawFeedFetchEntity::getFetchedAt))
                .toList();

        assertThat(rawFeeds)
                .extracting(RawFeedFetchEntity::getFeedType)
                .containsExactly(FeedType.PFS, FeedType.FUEL_PRICES, FeedType.PFS, FeedType.FUEL_PRICES);
        assertThat(rawFeeds)
                .extracting(RawFeedFetchEntity::getRecordCount)
                .containsExactly(1, 1, 1, 1);
    }

    private static PfsStationDto pfsStation(String siteId, String brand, Double latitude, Double longitude) {
        return new PfsStationDto(
                siteId,
                null,
                brand,
                true,
                brand,
                false,
                false,
                null,
                false,
                false,
                new PfsLocationDto(null, null, null, null, null, null, latitude, longitude),
                List.of(),
                null,
                List.of("E10", "B7")
        );
    }

    private static FuelPricesStationDto fuelPricesStation(
            String siteId,
            String fuelTypeOne,
            String priceOne,
            String fuelTypeTwo,
            String priceTwo
    ) {
        return FuelPricesStationDto.builder()
                .nodeId(siteId)
                .tradingName("Shell")
                .fuelPrices(List.of(
                        FuelPriceDto.builder().fuelType(fuelTypeOne).price(new BigDecimal(priceOne)).build(),
                        FuelPriceDto.builder().fuelType(fuelTypeTwo).price(new BigDecimal(priceTwo)).build()
                ))
                .build();
    }
}
