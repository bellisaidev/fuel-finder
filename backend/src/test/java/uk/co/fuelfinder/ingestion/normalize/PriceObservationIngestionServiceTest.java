package uk.co.fuelfinder.ingestion.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @InjectMocks
    private PriceObservationIngestionService priceObservationIngestionService;

    @Test
    void ingestStoresObservationAndProjectsLatestPrice() {
        RetailerEntity retailer = retailer();
        RawFeedFetchEntity rawFeedFetch = rawFeedFetch();
        FuelPricesStationDto stationDto = FuelPricesStationDto.builder().nodeId("site-1").build();
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

        int inserted = priceObservationIngestionService.ingest(retailer, rawFeedFetch, List.of(stationDto));

        assertEquals(1, inserted);

        ArgumentCaptor<PriceObservationEntity> captor = ArgumentCaptor.forClass(PriceObservationEntity.class);
        verify(priceObservationRepository).save(captor.capture());
        PriceObservationEntity saved = captor.getValue();
        assertEquals(station, saved.getStation());
        assertEquals("E10", saved.getFuelType());
        assertEquals(146, saved.getPricePence());
        assertEquals("GBP", saved.getCurrency());
        assertEquals(rawFeedFetch, saved.getRawPayload());
        verify(latestPriceProjectionService).upsertFromObservation(saved);
    }

    @Test
    void ingestSkipsObservationWhenStationDoesNotExist() {
        RetailerEntity retailer = retailer();
        RawFeedFetchEntity rawFeedFetch = rawFeedFetch();
        FuelPricesStationDto stationDto = FuelPricesStationDto.builder().nodeId("missing-site").build();
        NormalizedPriceObservation normalized = NormalizedPriceObservation.builder()
                .siteId("missing-site")
                .fuelType("B7")
                .price(new BigDecimal("1.529"))
                .build();

        when(fuelPricesNormalizer.normalize(stationDto)).thenReturn(List.of(normalized));
        when(stationRepository.findByRetailerAndSiteId(retailer, "missing-site")).thenReturn(Optional.empty());

        int inserted = priceObservationIngestionService.ingest(retailer, rawFeedFetch, List.of(stationDto));

        assertEquals(0, inserted);
        verify(priceObservationRepository, never()).save(any());
        verify(latestPriceProjectionService, never()).upsertFromObservation(any());
    }

    @Test
    void ingestSkipsDuplicateObservation() {
        RetailerEntity retailer = retailer();
        RawFeedFetchEntity rawFeedFetch = rawFeedFetch();
        FuelPricesStationDto stationDto = FuelPricesStationDto.builder().nodeId("site-2").build();
        NormalizedPriceObservation normalized = NormalizedPriceObservation.builder()
                .siteId("site-2")
                .fuelType("B7")
                .price(new BigDecimal("1.529"))
                .build();
        StationEntity station = stationEntity(retailer, "site-2");

        when(fuelPricesNormalizer.normalize(stationDto)).thenReturn(List.of(normalized));
        when(stationRepository.findByRetailerAndSiteId(retailer, "site-2")).thenReturn(Optional.of(station));
        when(priceObservationRepository.existsBySourceHash(any())).thenReturn(true);

        int inserted = priceObservationIngestionService.ingest(retailer, rawFeedFetch, List.of(stationDto));

        assertEquals(0, inserted);
        verify(priceObservationRepository, never()).save(any());
        verify(latestPriceProjectionService, never()).upsertFromObservation(any());
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

    private static StationEntity stationEntity(RetailerEntity retailer, String siteId) {
        return StationEntity.builder()
                .id(UUID.randomUUID())
                .retailer(retailer)
                .siteId(siteId)
                .build();
    }
}
