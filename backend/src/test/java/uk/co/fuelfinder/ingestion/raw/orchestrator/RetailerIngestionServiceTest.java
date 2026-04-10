package uk.co.fuelfinder.ingestion.raw.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.ingestion.normalize.LatestPriceProjectionService;
import uk.co.fuelfinder.ingestion.normalize.NormalizedStation;
import uk.co.fuelfinder.ingestion.normalize.PfsStationNormalizer;
import uk.co.fuelfinder.ingestion.normalize.PriceObservationIngestionService;
import uk.co.fuelfinder.ingestion.normalize.StationUpsertService;
import uk.co.fuelfinder.ingestion.raw.FeedType;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderFuelPricesClient;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderPfsClient;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPriceDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsLocationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;
import uk.co.fuelfinder.ingestion.raw.writer.RawFeedStorageService;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetailerIngestionServiceTest {

    @Mock
    private FuelFinderPfsClient pfsClient;

    @Mock
    private FuelFinderFuelPricesClient fuelPricesClient;

    @Mock
    private RawFeedStorageService rawFeedStorageService;

    @Mock
    private PfsStationNormalizer pfsStationNormalizer;

    @Mock
    private StationUpsertService stationUpsertService;

    @Mock
    private PriceObservationIngestionService priceObservationIngestionService;

    @Mock
    private LatestPriceProjectionService latestPriceProjectionService;

    @InjectMocks
    private RetailerIngestionService retailerIngestionService;

    @Test
    void ingestReturnsSuccessSummaryAndProcessesAllBatches() {
        RetailerEntity retailer = retailer("Shell");
        PfsStationDto pfsStation = pfsStation("site-1");
        FuelPricesStationDto fuelPricesStation = fuelPricesStation("site-1");
        NormalizedStation normalizedStation = NormalizedStation.builder()
                .siteId("site-1")
                .brand("Shell")
                .active(true)
                .build();
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);
        RawFeedFetchEntity fuelPricesRawFeed = rawFeedFetch(FeedType.FUEL_PRICES);

        when(pfsClient.fetchBatch(1)).thenReturn(repeat(pfsStation, 500));
        when(pfsClient.fetchBatch(2)).thenReturn(List.of(pfsStation));
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(repeat(fuelPricesStation, 500));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(List.of(fuelPricesStation));
        when(rawFeedStorageService.store(
                retailer,
                FeedType.PFS,
                "/pfs",
                1,
                repeat(pfsStation, 500, 1),
                501
        )).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(
                retailer,
                FeedType.FUEL_PRICES,
                "/pfs/fuel-prices",
                1,
                repeat(fuelPricesStation, 500, 1),
                501
        )).thenReturn(fuelPricesRawFeed);
        when(pfsStationNormalizer.normalize(any(PfsStationDto.class))).thenReturn(normalizedStation);
        when(stationUpsertService.upsert(retailer, normalizedStation)).thenReturn(1);
        when(priceObservationIngestionService.ingest(eq(retailer), eq(fuelPricesRawFeed), any(ArrayList.class)))
                .thenReturn(501);
        when(latestPriceProjectionService.backfillIfEmpty()).thenReturn(10);

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertTrue(summary.isSuccess());
        assertEquals("Shell", summary.getRetailerName());
        assertEquals(501, summary.getPfsRecordCount());
        assertEquals(501, summary.getFuelPricesRecordCount());
        assertEquals(pfsRawFeed.getId(), summary.getPfsRawFeedFetchId());
        assertEquals(fuelPricesRawFeed.getId(), summary.getFuelPricesRawFeedFetchId());
        assertEquals(1, summary.getPfsBatchNumber());
        assertEquals(1, summary.getFuelPricesBatchNumber());

        verify(pfsClient).fetchBatch(1);
        verify(pfsClient).fetchBatch(2);
        verify(fuelPricesClient).fetchFuelPrices(1);
        verify(fuelPricesClient).fetchFuelPrices(2);
        verify(rawFeedStorageService).store(retailer, FeedType.PFS, "/pfs", 1, repeat(pfsStation, 500, 1), 501);
        verify(rawFeedStorageService).store(
                retailer,
                FeedType.FUEL_PRICES,
                "/pfs/fuel-prices",
                1,
                repeat(fuelPricesStation, 500, 1),
                501
        );
        verify(stationUpsertService, times(501)).upsert(retailer, normalizedStation);
        verify(latestPriceProjectionService).backfillIfEmpty();
    }

    @Test
    void ingestReturnsFailureSummaryWhenPfsFetchFails() {
        RetailerEntity retailer = retailer("Tesco");

        when(pfsClient.fetchBatch(1)).thenThrow(new RuntimeException("PFS down"));

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertFalse(summary.isSuccess());
        assertEquals("Tesco", summary.getRetailerName());
        assertEquals("PFS down", summary.getFailureReason());
        assertEquals(0, summary.getPfsRecordCount());
        assertEquals(0, summary.getFuelPricesRecordCount());

        verify(rawFeedStorageService, never()).store(any(), any(), any(), any(Integer.class), any(), any(Integer.class));
        verify(priceObservationIngestionService, never()).ingest(any(), any(), any());
        verify(latestPriceProjectionService, never()).backfillIfEmpty();
    }

    @Test
    void ingestPassesFuelPriceStationsAsMutableArrayListToObservationIngestion() {
        RetailerEntity retailer = retailer("BP");
        PfsStationDto pfsStation = pfsStation("site-7");
        FuelPricesStationDto fuelPricesStation = fuelPricesStation("site-7");
        NormalizedStation normalizedStation = NormalizedStation.builder().siteId("site-7").active(true).build();
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);
        RawFeedFetchEntity fuelPricesRawFeed = rawFeedFetch(FeedType.FUEL_PRICES);

        when(pfsClient.fetchBatch(1)).thenReturn(List.of(pfsStation));
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(fuelPricesStation));
        when(rawFeedStorageService.store(retailer, FeedType.PFS, "/pfs", 1, List.of(pfsStation), 1)).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(
                retailer,
                FeedType.FUEL_PRICES,
                "/pfs/fuel-prices",
                1,
                List.of(fuelPricesStation),
                1
        )).thenReturn(fuelPricesRawFeed);
        when(pfsStationNormalizer.normalize(pfsStation)).thenReturn(normalizedStation);
        when(stationUpsertService.upsert(retailer, normalizedStation)).thenReturn(1);

        retailerIngestionService.ingest(retailer);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ArrayList<FuelPricesStationDto>> captor = ArgumentCaptor.forClass(ArrayList.class);
        verify(priceObservationIngestionService).ingest(eq(retailer), eq(fuelPricesRawFeed), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(fuelPricesStation, captor.getValue().getFirst());
    }

    private static RetailerEntity retailer(String name) {
        RetailerEntity retailer = new RetailerEntity();
        retailer.setId(UUID.randomUUID());
        retailer.setName(name);
        return retailer;
    }

    private static RawFeedFetchEntity rawFeedFetch(FeedType feedType) {
        return RawFeedFetchEntity.builder()
                .id(UUID.randomUUID())
                .feedType(feedType)
                .build();
    }

    private static PfsStationDto pfsStation(String siteId) {
        return new PfsStationDto(
                siteId,
                null,
                "Trading",
                true,
                "Brand",
                false,
                false,
                null,
                false,
                false,
                new PfsLocationDto(null, null, null, null, null, null, 51.5, -0.1),
                List.of(),
                null,
                List.of("E10")
        );
    }

    private static FuelPricesStationDto fuelPricesStation(String siteId) {
        return FuelPricesStationDto.builder()
                .nodeId(siteId)
                .tradingName("Brand")
                .fuelPrices(List.of(FuelPriceDto.builder().fuelType("E10").price(new BigDecimal("1.459")).build()))
                .build();
    }

    private static <T> List<T> repeat(T value, int count) {
        return repeat(value, count, 0);
    }

    private static <T> List<T> repeat(T value, int firstCount, int secondCount) {
        List<T> values = new ArrayList<>(firstCount + secondCount);
        for (int index = 0; index < firstCount + secondCount; index++) {
            values.add(value);
        }
        return values;
    }
}
