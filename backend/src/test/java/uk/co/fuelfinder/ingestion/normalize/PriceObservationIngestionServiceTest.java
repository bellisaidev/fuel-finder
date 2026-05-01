package uk.co.fuelfinder.ingestion.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.api.station.LatestPricesChangedEvent;
import uk.co.fuelfinder.api.station.PriceObservationsChangedEvent;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPriceDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceObservationIngestionServiceTest {

    @Mock
    private FuelPricesNormalizer fuelPricesNormalizer;

    @Mock
    private StationRepository stationRepository;

    @Mock
    private PriceObservationRepository priceObservationRepository;

    @Mock
    private LatestPriceProjectionService latestPriceProjectionService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PriceObservationIngestionService priceObservationIngestionService;

    @Test
    void ingestStoresObservationAndProjectsLatestPrice() {
        RetailerEntity retailer = retailer();
        RawFeedFetchEntity rawFeedFetch = rawFeedFetch();
        FuelPricesStationDto stationDto = fuelPricesStation("site-1", "E10", "1.459");
        NormalizedPriceObservation normalized = NormalizedPriceObservation.builder()
                .siteId("site-1")
                .fuelType("E10")
                .price(new BigDecimal("1.459"))
                .build();
        StationEntity station = stationEntity(retailer, "site-1");

        when(fuelPricesNormalizer.normalize(stationDto)).thenReturn(List.of(normalized));
        when(stationRepository.findByRetailerAndSiteId(retailer, "site-1")).thenReturn(Optional.of(station));
        when(priceObservationRepository.existsBySourceHash(any())).thenReturn(false);
        when(priceObservationRepository.save(any(PriceObservationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FuelPriceProcessingSummary summary = priceObservationIngestionService.ingest(
                retailer,
                rawFeedFetch,
                List.of(stationDto)
        );

        assertEquals(1, summary.rawStationCount());
        assertEquals(1, summary.rawFuelPriceEntryCount());
        assertEquals(1, summary.normalizedObservationCount());
        assertEquals(0, summary.skippedInvalidUnusableEntryCount());
        assertEquals(1, summary.insertedCount());
        assertEquals(0, summary.duplicateCount());
        assertEquals(0, summary.missingStationCount());

        ArgumentCaptor<PriceObservationEntity> captor = ArgumentCaptor.forClass(PriceObservationEntity.class);
        verify(priceObservationRepository).save(captor.capture());
        PriceObservationEntity saved = captor.getValue();
        assertEquals(station, saved.getStation());
        assertEquals("E10", saved.getFuelType());
        assertEquals(146, saved.getPricePence());
        assertEquals("GBP", saved.getCurrency());
        assertEquals(rawFeedFetch, saved.getRawPayload());
        verify(latestPriceProjectionService).upsertFromObservation(saved);
        verify(applicationEventPublisher).publishEvent(new PriceObservationsChangedEvent("price-observation-ingestion"));
        verify(applicationEventPublisher).publishEvent(new LatestPricesChangedEvent("price-observation-ingestion"));
    }

    @Test
    void ingestSkipsObservationWhenStationDoesNotExist() {
        RetailerEntity retailer = retailer();
        RawFeedFetchEntity rawFeedFetch = rawFeedFetch();
        FuelPricesStationDto stationDto = fuelPricesStation("missing-site", "B7", "1.529");
        NormalizedPriceObservation normalized = NormalizedPriceObservation.builder()
                .siteId("missing-site")
                .fuelType("B7")
                .price(new BigDecimal("1.529"))
                .build();

        when(fuelPricesNormalizer.normalize(stationDto)).thenReturn(List.of(normalized));
        when(stationRepository.findByRetailerAndSiteId(retailer, "missing-site")).thenReturn(Optional.empty());

        FuelPriceProcessingSummary summary = priceObservationIngestionService.ingest(
                retailer,
                rawFeedFetch,
                List.of(stationDto)
        );

        assertEquals(0, summary.insertedCount());
        assertEquals(0, summary.duplicateCount());
        assertEquals(1, summary.missingStationCount());
        assertEquals(1, summary.normalizedObservationCount());
        verify(priceObservationRepository, never()).save(any());
        verify(latestPriceProjectionService, never()).upsertFromObservation(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void ingestSkipsDuplicateObservation() {
        RetailerEntity retailer = retailer();
        RawFeedFetchEntity rawFeedFetch = rawFeedFetch();
        FuelPricesStationDto stationDto = fuelPricesStation("site-2", "B7", "1.529");
        NormalizedPriceObservation normalized = NormalizedPriceObservation.builder()
                .siteId("site-2")
                .fuelType("B7")
                .price(new BigDecimal("1.529"))
                .build();
        StationEntity station = stationEntity(retailer, "site-2");

        when(fuelPricesNormalizer.normalize(stationDto)).thenReturn(List.of(normalized));
        when(stationRepository.findByRetailerAndSiteId(retailer, "site-2")).thenReturn(Optional.of(station));
        when(priceObservationRepository.existsBySourceHash(any())).thenReturn(true);

        FuelPriceProcessingSummary summary = priceObservationIngestionService.ingest(
                retailer,
                rawFeedFetch,
                List.of(stationDto)
        );

        assertEquals(0, summary.insertedCount());
        assertEquals(1, summary.duplicateCount());
        assertEquals(0, summary.missingStationCount());
        assertEquals(1, summary.normalizedObservationCount());
        verify(priceObservationRepository, never()).save(any());
        verify(latestPriceProjectionService, never()).upsertFromObservation(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void ingestCountsInvalidUnusableFuelPriceEntriesAsNormalizationSkips() {
        RetailerEntity retailer = retailer();
        RawFeedFetchEntity rawFeedFetch = rawFeedFetch();
        FuelPricesStationDto stationDto = fuelPricesStation("site-3", "E10", "1.459");

        when(fuelPricesNormalizer.normalize(stationDto)).thenReturn(List.of());

        FuelPriceProcessingSummary summary = priceObservationIngestionService.ingest(
                retailer,
                rawFeedFetch,
                List.of(stationDto)
        );

        assertEquals(1, summary.rawStationCount());
        assertEquals(1, summary.rawFuelPriceEntryCount());
        assertEquals(0, summary.normalizedObservationCount());
        assertEquals(1, summary.skippedInvalidUnusableEntryCount());
        assertEquals(0, summary.insertedCount());
        assertEquals(0, summary.duplicateCount());
        assertEquals(0, summary.missingStationCount());
        verify(priceObservationRepository, never()).save(any());
    }

    @Test
    void ingestRejectsNullRetailer() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> priceObservationIngestionService.ingest(null, rawFeedFetch(), List.of())
        );

        assertEquals("Retailer cannot be null", exception.getMessage());
    }

    private static RetailerEntity retailer() {
        RetailerEntity retailer = new RetailerEntity();
        retailer.setId(UUID.randomUUID());
        retailer.setName("Shell");
        retailer.setCreatedAt(OffsetDateTime.now());
        retailer.setUpdatedAt(OffsetDateTime.now());
        return retailer;
    }

    private static RawFeedFetchEntity rawFeedFetch() {
        return RawFeedFetchEntity.builder()
                .id(UUID.randomUUID())
                .build();
    }

    private static FuelPricesStationDto fuelPricesStation(String siteId, String fuelType, String price) {
        return FuelPricesStationDto.builder()
                .nodeId(siteId)
                .fuelPrices(List.of(FuelPriceDto.builder()
                        .fuelType(fuelType)
                        .price(new BigDecimal(price))
                        .build()))
                .build();
    }

    private static StationEntity stationEntity(RetailerEntity retailer, String siteId) {
        return StationEntity.builder()
                .id(UUID.randomUUID())
                .retailer(retailer)
                .siteId(siteId)
                .build();
    }
}
