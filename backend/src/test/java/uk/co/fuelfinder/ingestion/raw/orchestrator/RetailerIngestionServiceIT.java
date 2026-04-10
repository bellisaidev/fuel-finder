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
import uk.co.fuelfinder.persistence.entity.LatestPriceEntity;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.LatestPriceRepository;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.RawFeedFetchRepository;
import uk.co.fuelfinder.persistence.repository.RetailerRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RetailerIngestionServiceIT {

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
    void ingestPersistsRawFeedsStationsObservationsAndLatestPrices() {
        when(pfsClient.fetchBatch(1)).thenReturn(List.of(
                pfsStation("site-1", "Shell", 51.5007, -0.1246),
                pfsStation("site-2", "Shell", 51.5010, -0.1416)
        ));
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(
                fuelPricesStation("site-1", "E10", "1.459", "B7", "1.529"),
                fuelPricesStation("site-2", "E10", "1.489")
        ));

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertThat(summary.isSuccess()).isTrue();
        assertThat(summary.getPfsRecordCount()).isEqualTo(2);
        assertThat(summary.getFuelPricesRecordCount()).isEqualTo(2);
        assertThat(summary.getPfsRawFeedFetchId()).isNotNull();
        assertThat(summary.getFuelPricesRawFeedFetchId()).isNotNull();

        List<RawFeedFetchEntity> rawFeeds = rawFeedFetchRepository.findAll();
        assertThat(rawFeeds).hasSize(2);
        assertThat(rawFeeds)
                .extracting(RawFeedFetchEntity::getFeedType)
                .containsExactlyInAnyOrder(FeedType.PFS, FeedType.FUEL_PRICES);
        assertThat(rawFeeds)
                .extracting(RawFeedFetchEntity::getRecordCount)
                .containsExactlyInAnyOrder(2, 2);

        List<StationEntity> stations = stationRepository.findAll();
        assertThat(stations).hasSize(2);
        assertThat(stations)
                .extracting(StationEntity::getSiteId)
                .containsExactlyInAnyOrder("site-1", "site-2");
        assertThat(stations)
                .allSatisfy(station -> {
                    assertThat(station.getLocation()).isNotNull();
                    assertThat(station.getLocation().getSRID()).isEqualTo(4326);
                });

        assertThat(priceObservationRepository.count()).isEqualTo(3);

        List<LatestPriceEntity> latestPrices = latestPriceRepository.findAll();
        assertThat(latestPrices).hasSize(3);
        assertThat(latestPrices.stream()
                .map(LatestPriceEntity::getPricePence)
                .sorted()
                .toList()).containsExactly(146, 149, 153);
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

    private static FuelPricesStationDto fuelPricesStation(String siteId, String fuelTypeOne, String priceOne) {
        return FuelPricesStationDto.builder()
                .nodeId(siteId)
                .tradingName("Shell")
                .fuelPrices(List.of(
                        FuelPriceDto.builder().fuelType(fuelTypeOne).price(new BigDecimal(priceOne)).build()
                ))
                .build();
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
