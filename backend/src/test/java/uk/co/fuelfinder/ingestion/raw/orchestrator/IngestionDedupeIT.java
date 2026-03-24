//package uk.co.fuelfinder.ingestion;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.utility.DockerImageName;
//import uk.co.fuelfinder.ingestion.orchestrator.RetailerIngestionService;
//import uk.co.fuelfinder.ingestion.fetch.RetailerFeedClient;
//import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
//import uk.co.fuelfinder.persistence.repository..RetailerRepository;
//import uk.co.fuelfinder.persistence.repository..StationRepository;
//import uk.co.fuelfinder.persistence.repository..LatestPriceRepository;
//import uk.co.fuelfinder.model.Retailer;
//
//import java.nio.charset.StandardCharsets;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Testcontainers
//@SpringBootTest
//class IngestionDedupeIT {
//
//    // PostGIS image (matcha la tua major di Postgres)
//    @Container
//    static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
//            DockerImageName.parse("postgis/postgis:16-3.4")
//                    .asCompatibleSubstituteFor("postgres")
//    );
//
//    @DynamicPropertySource
//    static void dbProps(DynamicPropertyRegistry r) {
//        r.add("spring.datasource.url", POSTGIS::getJdbcUrl);
//        r.add("spring.datasource.username", POSTGIS::getUsername);
//        r.add("spring.datasource.password", POSTGIS::getPassword);
//
//        // IMPORTANT: vogliamo Flyway in test
//        r.add("spring.flyway.enabled", () -> true);
//
//        // Se in prod hai ddl-auto: validate, ok lasciarlo così.
//        // r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
//    }
//
//    @Autowired
//    RetailerIngestionService ingestionService;
//    @Autowired RetailerRepository retailerRepository;
//    @Autowired StationRepository stationRepository;
//    @Autowired
//    PriceObservationRepository priceObservationRepository;
//    @Autowired LatestPriceRepository latestPriceRepository;
//
//    @MockBean RetailerFeedClient retailerFeedClient; // niente rete in test
//
//    private Retailer retailer;
//
//    @BeforeEach
//    void setup() {
//        // 1) crea retailer in DB (minimo necessario)
//        retailer = retailerRepository.findByCode("SHELL").orElseGet(() -> {
//            Retailer r = Retailer.builder()
//                    .id(UUID.randomUUID())
//                    .code("SHELL")
//                    .name("Shell")
//                    .feedUrl("https://example.invalid/shell.json") // non verrà chiamato
//                    .active(true)
//                    .build();
//            return retailerRepository.save(r);
//        });
//
//        // 2) mock fetch -> fixture JSON costante
//        String json = loadFixture("/fixtures/shell_sample.json");
//
//        Mockito.when(retailerFeedClient.fetch(Mockito.anyString()))
//                .thenReturn(RetailerFeedClient.FetchResult.builder()
//                        .httpStatus(200)
//                        .body(json)
//                        .error(null)
//                        .build());
//    }
//
//    @Test
//    void samePayloadTwice_shouldNotCreateNewObservationsSecondTime() {
//        long obsBefore = priceObservationRepository.count();
//
//        var s1 = ingestionService.ingest(retailer);
//        long obsAfter1 = priceObservationRepository.count();
//
//        var s2 = ingestionService.ingest(retailer);
//        long obsAfter2 = priceObservationRepository.count();
//
//        assertThat(s1.isSuccess()).isTrue();
//        assertThat(s2.isSuccess()).isTrue();
//
//        assertThat(obsAfter1).isGreaterThan(obsBefore);
//        assertThat(obsAfter2).isEqualTo(obsAfter1); // ✅ dedupe
//
//        // opzionali ma utili:
//        assertThat(stationRepository.count()).isGreaterThan(0);
//        assertThat(latestPriceRepository.count()).isGreaterThan(0);
//    }
//
//    private static String loadFixture(String path) {
//        try (var in = IngestionDedupeIT.class.getResourceAsStream(path)) {
//            if (in == null) throw new IllegalStateException("Fixture not found: " + path);
//            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
//        } catch (Exception e) {
//            throw new IllegalStateException("Failed to load fixture: " + path, e);
//        }
//    }
//}
