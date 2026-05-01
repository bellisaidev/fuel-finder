package uk.co.fuelfinder.ingestion.raw.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.ingestion.normalize.FuelPriceProcessingSummary;
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

    @Mock
    private IngestionReconciliationProperties reconciliationProperties;

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
        when(pfsClient.fetchBatch(3)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(repeat(fuelPricesStation, 500));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(List.of(fuelPricesStation));
        when(fuelPricesClient.fetchFuelPrices(3)).thenReturn(List.of());
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
                .thenReturn(fuelPriceSummary(501, 501, 501, 501));
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
        assertEquals(ReconciliationStatus.OK, summary.getReconciliationStatus());
        assertEquals(ReconciliationAction.FAIL, summary.getReconciliationAction());
        assertFalse(summary.isReconciliationShouldAbort());

        verify(pfsClient).fetchBatch(1);
        verify(pfsClient).fetchBatch(2);
        verify(pfsClient).fetchBatch(3);
        verify(fuelPricesClient).fetchFuelPrices(1);
        verify(fuelPricesClient).fetchFuelPrices(2);
        verify(fuelPricesClient).fetchFuelPrices(3);
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
    void ingestContinuesFetchingWhenBatchContainsFewerThanPreviousPageSize() {
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

        when(pfsClient.fetchBatch(1)).thenReturn(repeat(pfsStation, 498));
        when(pfsClient.fetchBatch(2)).thenReturn(repeat(pfsStation, 498));
        when(pfsClient.fetchBatch(3)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(repeat(fuelPricesStation, 498));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(repeat(fuelPricesStation, 498));
        when(fuelPricesClient.fetchFuelPrices(3)).thenReturn(List.of());
        when(rawFeedStorageService.store(
                retailer,
                FeedType.PFS,
                "/pfs",
                1,
                repeat(pfsStation, 498, 498),
                996
        )).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(
                retailer,
                FeedType.FUEL_PRICES,
                "/pfs/fuel-prices",
                1,
                repeat(fuelPricesStation, 498, 498),
                996
        )).thenReturn(fuelPricesRawFeed);
        when(pfsStationNormalizer.normalize(any(PfsStationDto.class))).thenReturn(normalizedStation);
        when(stationUpsertService.upsert(retailer, normalizedStation)).thenReturn(1);
        when(priceObservationIngestionService.ingest(eq(retailer), eq(fuelPricesRawFeed), any(ArrayList.class)))
                .thenReturn(fuelPriceSummary(996, 996, 996, 996));
        when(latestPriceProjectionService.backfillIfEmpty()).thenReturn(0);

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertTrue(summary.isSuccess());
        assertEquals(996, summary.getPfsRecordCount());
        assertEquals(996, summary.getFuelPricesRecordCount());
        verify(pfsClient).fetchBatch(3);
        verify(fuelPricesClient).fetchFuelPrices(3);
        verify(stationUpsertService, times(996)).upsert(retailer, normalizedStation);
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
        when(pfsClient.fetchBatch(2)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(fuelPricesStation));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(List.of());
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
        when(priceObservationIngestionService.ingest(eq(retailer), eq(fuelPricesRawFeed), any(ArrayList.class)))
                .thenReturn(fuelPriceSummary(1, 1, 1, 1));

        retailerIngestionService.ingest(retailer);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ArrayList<FuelPricesStationDto>> captor = ArgumentCaptor.forClass(ArrayList.class);
        verify(priceObservationIngestionService).ingest(eq(retailer), eq(fuelPricesRawFeed), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(fuelPricesStation, captor.getValue().getFirst());
    }

    @Test
    void ingestStoresEmptyFeedsAndSkipsDownstreamProcessingWhenBothResponsesAreEmpty() {
        RetailerEntity retailer = retailer("Asda");
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);
        RawFeedFetchEntity fuelPricesRawFeed = rawFeedFetch(FeedType.FUEL_PRICES);

        when(pfsClient.fetchBatch(1)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of());
        when(rawFeedStorageService.store(retailer, FeedType.PFS, "/pfs", 1, List.of(), 0)).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(retailer, FeedType.FUEL_PRICES, "/pfs/fuel-prices", 1, List.of(), 0))
                .thenReturn(fuelPricesRawFeed);
        when(priceObservationIngestionService.ingest(retailer, fuelPricesRawFeed, new ArrayList<>()))
                .thenReturn(fuelPriceSummary(0, 0, 0, 0));
        when(latestPriceProjectionService.backfillIfEmpty()).thenReturn(0);

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertTrue(summary.isSuccess());
        assertEquals(0, summary.getPfsRecordCount());
        assertEquals(0, summary.getFuelPricesRecordCount());
        assertEquals(pfsRawFeed.getId(), summary.getPfsRawFeedFetchId());
        assertEquals(fuelPricesRawFeed.getId(), summary.getFuelPricesRawFeedFetchId());
        assertEquals(ReconciliationStatus.OK, summary.getReconciliationStatus());

        verify(pfsClient).fetchBatch(1);
        verify(fuelPricesClient).fetchFuelPrices(1);
        verify(pfsStationNormalizer, never()).normalize(any());
        verify(stationUpsertService, never()).upsert(any(), any());
        verify(priceObservationIngestionService).ingest(eq(retailer), eq(fuelPricesRawFeed), eq(new ArrayList<>()));
        verify(latestPriceProjectionService).backfillIfEmpty();
    }

    @Test
    void ingestSkipsPfsStationsWithoutSiteId() {
        RetailerEntity retailer = retailer("Texaco");
        PfsStationDto validPfsStation = pfsStation("site-40");
        PfsStationDto invalidPfsStation = pfsStation(null);
        FuelPricesStationDto fuelPricesStation = fuelPricesStation("site-40");
        NormalizedStation validNormalizedStation = NormalizedStation.builder().siteId("site-40").active(true).build();
        NormalizedStation invalidNormalizedStation = NormalizedStation.builder().active(true).build();
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);
        RawFeedFetchEntity fuelPricesRawFeed = rawFeedFetch(FeedType.FUEL_PRICES);

        when(pfsClient.fetchBatch(1)).thenReturn(List.of(validPfsStation, invalidPfsStation));
        when(pfsClient.fetchBatch(2)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(fuelPricesStation));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(List.of());
        when(rawFeedStorageService.store(
                retailer,
                FeedType.PFS,
                "/pfs",
                1,
                List.of(validPfsStation, invalidPfsStation),
                2
        )).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(retailer, FeedType.FUEL_PRICES, "/pfs/fuel-prices", 1, List.of(fuelPricesStation), 1))
                .thenReturn(fuelPricesRawFeed);
        when(pfsStationNormalizer.normalize(validPfsStation)).thenReturn(validNormalizedStation);
        when(pfsStationNormalizer.normalize(invalidPfsStation)).thenReturn(invalidNormalizedStation);
        when(stationUpsertService.upsert(retailer, validNormalizedStation)).thenReturn(1);
        when(priceObservationIngestionService.ingest(retailer, fuelPricesRawFeed, new ArrayList<>(List.of(fuelPricesStation))))
                .thenReturn(fuelPriceSummary(1, 1, 1, 1));
        when(latestPriceProjectionService.backfillIfEmpty()).thenReturn(0);

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertTrue(summary.isSuccess());
        assertEquals(2, summary.getPfsRecordCount());
        assertEquals(ReconciliationStatus.OK_WITH_SKIPS, summary.getReconciliationStatus());
        verify(stationUpsertService).upsert(retailer, validNormalizedStation);
        verify(stationUpsertService, never()).upsert(retailer, invalidNormalizedStation);
    }

    @Test
    void ingestReturnsFailureSummaryWhenReconciliationFailsWithFailAction() {
        RetailerEntity retailer = retailer("ReconcileFail");
        PfsStationDto pfsStation = pfsStation("site-50");
        FuelPricesStationDto fuelPricesStation = fuelPricesStation("site-50");
        NormalizedStation normalizedStation = NormalizedStation.builder().siteId("site-50").active(true).build();
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);
        RawFeedFetchEntity fuelPricesRawFeed = rawFeedFetch(FeedType.FUEL_PRICES);

        when(pfsClient.fetchBatch(1)).thenReturn(List.of(pfsStation));
        when(pfsClient.fetchBatch(2)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(fuelPricesStation));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(List.of());
        when(rawFeedStorageService.store(retailer, FeedType.PFS, "/pfs", 1, List.of(pfsStation), 1)).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(retailer, FeedType.FUEL_PRICES, "/pfs/fuel-prices", 1, List.of(fuelPricesStation), 1))
                .thenReturn(fuelPricesRawFeed);
        when(pfsStationNormalizer.normalize(pfsStation)).thenReturn(normalizedStation);
        when(stationUpsertService.upsert(retailer, normalizedStation)).thenReturn(1);
        when(reconciliationProperties.getUnexplainedMismatchAction()).thenReturn(ReconciliationAction.FAIL);
        when(priceObservationIngestionService.ingest(retailer, fuelPricesRawFeed, new ArrayList<>(List.of(fuelPricesStation))))
                .thenReturn(unreconciledFuelPriceSummary());

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertFalse(summary.isSuccess());
        assertEquals(ReconciliationStatus.FAILED, summary.getReconciliationStatus());
        assertEquals(ReconciliationAction.FAIL, summary.getReconciliationAction());
        assertTrue(summary.isReconciliationShouldAbort());
        verify(latestPriceProjectionService, never()).backfillIfEmpty();
    }

    @Test
    void ingestContinuesWhenReconciliationFailsWithWarnAction() {
        RetailerEntity retailer = retailer("ReconcileWarn");
        PfsStationDto pfsStation = pfsStation("site-60");
        FuelPricesStationDto fuelPricesStation = fuelPricesStation("site-60");
        NormalizedStation normalizedStation = NormalizedStation.builder().siteId("site-60").active(true).build();
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);
        RawFeedFetchEntity fuelPricesRawFeed = rawFeedFetch(FeedType.FUEL_PRICES);

        when(pfsClient.fetchBatch(1)).thenReturn(List.of(pfsStation));
        when(pfsClient.fetchBatch(2)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(fuelPricesStation));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(List.of());
        when(rawFeedStorageService.store(retailer, FeedType.PFS, "/pfs", 1, List.of(pfsStation), 1)).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(retailer, FeedType.FUEL_PRICES, "/pfs/fuel-prices", 1, List.of(fuelPricesStation), 1))
                .thenReturn(fuelPricesRawFeed);
        when(pfsStationNormalizer.normalize(pfsStation)).thenReturn(normalizedStation);
        when(stationUpsertService.upsert(retailer, normalizedStation)).thenReturn(1);
        when(reconciliationProperties.getUnexplainedMismatchAction()).thenReturn(ReconciliationAction.WARN);
        when(priceObservationIngestionService.ingest(retailer, fuelPricesRawFeed, new ArrayList<>(List.of(fuelPricesStation))))
                .thenReturn(unreconciledFuelPriceSummary());
        when(latestPriceProjectionService.backfillIfEmpty()).thenReturn(0);

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertTrue(summary.isSuccess());
        assertEquals(ReconciliationStatus.FAILED, summary.getReconciliationStatus());
        assertEquals(ReconciliationAction.WARN, summary.getReconciliationAction());
        assertFalse(summary.isReconciliationShouldAbort());
        verify(latestPriceProjectionService).backfillIfEmpty();
    }

    @Test
    void ingestReturnsFailureSummaryWhenFuelPricesFetchFailsAfterPfsWasStored() {
        RetailerEntity retailer = retailer("Esso");
        PfsStationDto pfsStation = pfsStation("site-11");
        NormalizedStation normalizedStation = NormalizedStation.builder().siteId("site-11").active(true).build();
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);

        when(pfsClient.fetchBatch(1)).thenReturn(List.of(pfsStation));
        when(pfsClient.fetchBatch(2)).thenReturn(List.of());
        when(rawFeedStorageService.store(retailer, FeedType.PFS, "/pfs", 1, List.of(pfsStation), 1)).thenReturn(pfsRawFeed);
        when(pfsStationNormalizer.normalize(pfsStation)).thenReturn(normalizedStation);
        when(stationUpsertService.upsert(retailer, normalizedStation)).thenReturn(1);
        when(fuelPricesClient.fetchFuelPrices(1)).thenThrow(new RuntimeException("Fuel prices down"));

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertFalse(summary.isSuccess());
        assertEquals("Esso", summary.getRetailerName());
        assertEquals("Fuel prices down", summary.getFailureReason());
        assertEquals(0, summary.getPfsRecordCount());
        assertEquals(0, summary.getFuelPricesRecordCount());

        verify(rawFeedStorageService).store(retailer, FeedType.PFS, "/pfs", 1, List.of(pfsStation), 1);
        verify(rawFeedStorageService, never()).store(eq(retailer), eq(FeedType.FUEL_PRICES), any(), any(Integer.class), any(), any(Integer.class));
        verify(stationUpsertService).upsert(retailer, normalizedStation);
        verify(priceObservationIngestionService, never()).ingest(any(), any(), any());
        verify(latestPriceProjectionService, never()).backfillIfEmpty();
    }

    @Test
    void ingestReturnsFailureSummaryWhenPfsStorageFails() {
        RetailerEntity retailer = retailer("Morrisons");
        PfsStationDto pfsStation = pfsStation("site-20");

        when(pfsClient.fetchBatch(1)).thenReturn(List.of(pfsStation));
        when(pfsClient.fetchBatch(2)).thenReturn(List.of());
        when(rawFeedStorageService.store(retailer, FeedType.PFS, "/pfs", 1, List.of(pfsStation), 1))
                .thenThrow(new IllegalStateException("PFS store failed"));

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertFalse(summary.isSuccess());
        assertEquals("PFS store failed", summary.getFailureReason());

        verify(pfsStationNormalizer, never()).normalize(any());
        verify(fuelPricesClient, never()).fetchFuelPrices(any(Integer.class));
        verify(priceObservationIngestionService, never()).ingest(any(), any(), any());
        verify(latestPriceProjectionService, never()).backfillIfEmpty();
    }

    @Test
    void ingestReturnsFailureSummaryWhenBackfillFailsAtEnd() {
        RetailerEntity retailer = retailer("Jet");
        PfsStationDto pfsStation = pfsStation("site-30");
        FuelPricesStationDto fuelPricesStation = fuelPricesStation("site-30");
        NormalizedStation normalizedStation = NormalizedStation.builder().siteId("site-30").active(true).build();
        RawFeedFetchEntity pfsRawFeed = rawFeedFetch(FeedType.PFS);
        RawFeedFetchEntity fuelPricesRawFeed = rawFeedFetch(FeedType.FUEL_PRICES);

        when(pfsClient.fetchBatch(1)).thenReturn(List.of(pfsStation));
        when(pfsClient.fetchBatch(2)).thenReturn(List.of());
        when(fuelPricesClient.fetchFuelPrices(1)).thenReturn(List.of(fuelPricesStation));
        when(fuelPricesClient.fetchFuelPrices(2)).thenReturn(List.of());
        when(rawFeedStorageService.store(retailer, FeedType.PFS, "/pfs", 1, List.of(pfsStation), 1)).thenReturn(pfsRawFeed);
        when(rawFeedStorageService.store(retailer, FeedType.FUEL_PRICES, "/pfs/fuel-prices", 1, List.of(fuelPricesStation), 1))
                .thenReturn(fuelPricesRawFeed);
        when(pfsStationNormalizer.normalize(pfsStation)).thenReturn(normalizedStation);
        when(stationUpsertService.upsert(retailer, normalizedStation)).thenReturn(1);
        when(priceObservationIngestionService.ingest(retailer, fuelPricesRawFeed, new ArrayList<>(List.of(fuelPricesStation))))
                .thenReturn(fuelPriceSummary(1, 1, 1, 1));
        when(latestPriceProjectionService.backfillIfEmpty()).thenThrow(new IllegalStateException("Backfill failed"));

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        assertFalse(summary.isSuccess());
        assertEquals("Jet", summary.getRetailerName());
        assertEquals("Backfill failed", summary.getFailureReason());

        verify(priceObservationIngestionService).ingest(eq(retailer), eq(fuelPricesRawFeed), any(ArrayList.class));
        verify(latestPriceProjectionService).backfillIfEmpty();
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

    private static FuelPriceProcessingSummary fuelPriceSummary(
            int rawStationCount,
            int rawFuelPriceEntryCount,
            int normalizedObservationCount,
            int insertedCount
    ) {
        return FuelPriceProcessingSummary.builder()
                .rawStationCount(rawStationCount)
                .rawFuelPriceEntryCount(rawFuelPriceEntryCount)
                .normalizedObservationCount(normalizedObservationCount)
                .skippedInvalidUnusableEntryCount(Math.max(rawFuelPriceEntryCount - normalizedObservationCount, 0))
                .insertedCount(insertedCount)
                .duplicateCount(0)
                .missingStationCount(0)
                .otherPersistenceSkipCount(0)
                .build();
    }

    private static FuelPriceProcessingSummary unreconciledFuelPriceSummary() {
        return FuelPriceProcessingSummary.builder()
                .rawStationCount(1)
                .rawFuelPriceEntryCount(2)
                .normalizedObservationCount(1)
                .skippedInvalidUnusableEntryCount(0)
                .insertedCount(1)
                .duplicateCount(0)
                .missingStationCount(0)
                .otherPersistenceSkipCount(0)
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
