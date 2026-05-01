package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.ingestion.normalize.FuelPriceProcessingSummary;
import uk.co.fuelfinder.ingestion.normalize.NormalizedStation;
import uk.co.fuelfinder.ingestion.normalize.PfsStationNormalizer;
import uk.co.fuelfinder.ingestion.normalize.LatestPriceProjectionService;
import uk.co.fuelfinder.ingestion.normalize.PriceObservationIngestionService;
import uk.co.fuelfinder.ingestion.normalize.StationUpsertService;
import uk.co.fuelfinder.ingestion.raw.FeedType;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderFuelPricesClient;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderPfsClient;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;
import uk.co.fuelfinder.ingestion.raw.writer.RawFeedStorageService;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetailerIngestionService {

    private static final int DEFAULT_BATCH_NUMBER = 1;
    private static final int MAX_BATCH_COUNT = 100;

    private static final String PFS_ENDPOINT_PATH = "/pfs";
    private static final String FUEL_PRICES_ENDPOINT_PATH = "/pfs/fuel-prices";

    private final FuelFinderPfsClient pfsClient;
    private final FuelFinderFuelPricesClient fuelPricesClient;
    private final RawFeedStorageService rawFeedStorageService;
    private final PfsStationNormalizer pfsStationNormalizer;
    private final StationUpsertService stationUpsertService;
    private final PriceObservationIngestionService priceObservationIngestionService;
    private final LatestPriceProjectionService latestPriceProjectionService;
    private final IngestionReconciliationProperties reconciliationProperties;

    public RawIngestionSummary ingest(RetailerEntity retailer) {
        Instant startedAt = Instant.now();
        String retailerName = retailer.getName();

        log.info("Starting ingestion for retailer={}", retailerName);

        try {
            PagedFetchResult<PfsStationDto> pfsResult = fetchAllPfsStations(retailerName);
            List<PfsStationDto> pfsStations = pfsResult.records();

            RawFeedFetchEntity pfsRawFeed = rawFeedStorageService.store(
                    retailer,
                    FeedType.PFS,
                    PFS_ENDPOINT_PATH,
                    DEFAULT_BATCH_NUMBER,
                    pfsStations,
                    pfsStations.size()
            );

            PfsStationProcessingSummary pfsProcessingSummary = normalizeAndUpsertStations(retailer, pfsStations);
            log.info(
                    "Station normalization completed for retailer={}: rawStationCount={}, normalizedStationCount={}, skippedCount={}, skippedMissingSiteIdCount={}, stationUpserts={}",
                    retailerName,
                    pfsProcessingSummary.rawStationCount(),
                    pfsProcessingSummary.normalizedStationCount(),
                    pfsProcessingSummary.skippedCount(),
                    pfsProcessingSummary.skippedMissingSiteIdCount(),
                    pfsProcessingSummary.stationUpsertCount()
            );

            PagedFetchResult<FuelPricesStationDto> fuelPricesResult = fetchAllFuelPrices(retailerName);
            List<FuelPricesStationDto> fuelPricesStations = fuelPricesResult.records();

            RawFeedFetchEntity fuelPricesRawFeed = rawFeedStorageService.store(
                    retailer,
                    FeedType.FUEL_PRICES,
                    FUEL_PRICES_ENDPOINT_PATH,
                    DEFAULT_BATCH_NUMBER,
                    fuelPricesStations,
                    fuelPricesStations.size()
            );

            FuelPriceProcessingSummary fuelPriceProcessingSummary = priceObservationIngestionService.ingest(
                    retailer,
                    fuelPricesRawFeed,
                    new ArrayList<>(fuelPricesStations)
            );
            ReconciliationDecision reconciliationDecision = ReconciliationDecision.evaluate(
                    pfsProcessingSummary,
                    fuelPriceProcessingSummary,
                    reconciliationProperties.getUnexplainedMismatchAction()
            );
            logReconciliationSummary(
                    retailerName,
                    pfsRawFeed,
                    fuelPricesRawFeed,
                    pfsProcessingSummary,
                    fuelPriceProcessingSummary,
                    reconciliationDecision
            );

            if (reconciliationDecision.shouldAbort()) {
                throw new ReconciliationException(reconciliationDecision);
            }

            int latestPricesBackfilled = latestPriceProjectionService.backfillIfEmpty();

            RawIngestionSummary summary = RawIngestionSummary.success(
                    retailerName,
                    startedAt,
                    DEFAULT_BATCH_NUMBER,
                    pfsStations.size(),
                    pfsRawFeed.getId(),
                    DEFAULT_BATCH_NUMBER,
                    fuelPricesStations.size(),
                    fuelPricesRawFeed.getId(),
                    reconciliationDecision
            );

            log.info(
                    "Completed ingestion for retailer={}: pfsRecordCount={}, pfsBatchCount={}, fuelPricesRecordCount={}, fuelPricesBatchCount={}, stationUpserts={}, observationsInserted={}, latestPricesBackfilled={}, pfsRawFeedFetchId={}, fuelPricesRawFeedFetchId={}",
                    retailerName,
                    summary.getPfsRecordCount(),
                    pfsResult.batchCount(),
                    summary.getFuelPricesRecordCount(),
                    fuelPricesResult.batchCount(),
                    pfsProcessingSummary.stationUpsertCount(),
                    fuelPriceProcessingSummary.insertedCount(),
                    latestPricesBackfilled,
                    summary.getPfsRawFeedFetchId(),
                    summary.getFuelPricesRawFeedFetchId()
            );

            return summary;

        } catch (ReconciliationException e) {
            log.error("Ingestion reconciliation failed for retailer={}: {}", retailerName, e.getMessage());

            return RawIngestionSummary.failed(
                    retailerName,
                    startedAt,
                    e.getMessage() == null ? "Unknown reconciliation error" : e.getMessage(),
                    e.getDecision()
            );
        } catch (Exception e) {
            log.error("Ingestion failed for retailer={}: {}", retailerName, e.getMessage(), e);

            return RawIngestionSummary.failed(
                    retailerName,
                    startedAt,
                    e.getMessage() == null ? "Unknown ingestion error" : e.getMessage()
            );
        }
    }

    private PagedFetchResult<PfsStationDto> fetchAllPfsStations(String retailerName) {
        List<PfsStationDto> allStations = new ArrayList<>();
        int batchNumber = DEFAULT_BATCH_NUMBER;
        int batchCount = 0;

        while (true) {
            List<PfsStationDto> batch = pfsClient.fetchBatch(batchNumber);

            log.info(
                    "Fetched PFS batch {} for retailer={}: {} stations",
                    batchNumber,
                    retailerName,
                    batch.size()
            );

            if (batch.isEmpty()) {
                break;
            }

            allStations.addAll(batch);
            batchCount++;

            batchNumber++;
            if (batchCount >= MAX_BATCH_COUNT) {
                throw new IllegalStateException("PFS pagination exceeded maximum batch count " + MAX_BATCH_COUNT);
            }
        }

        log.info(
                "Fetched all PFS batches for retailer={}: totalStations={}, batchCount={}",
                retailerName,
                allStations.size(),
                batchCount
        );

        return new PagedFetchResult<>(allStations, batchCount);
    }

    private PagedFetchResult<FuelPricesStationDto> fetchAllFuelPrices(String retailerName) {
        List<FuelPricesStationDto> allFuelPricesStations = new ArrayList<>();
        int batchNumber = DEFAULT_BATCH_NUMBER;
        int batchCount = 0;

        while (true) {
            List<FuelPricesStationDto> batch = fuelPricesClient.fetchFuelPrices(batchNumber);

            log.info(
                    "Fetched fuel prices batch {} for retailer={}: {} stations",
                    batchNumber,
                    retailerName,
                    batch.size()
            );

            if (batch.isEmpty()) {
                break;
            }

            allFuelPricesStations.addAll(batch);
            batchCount++;

            batchNumber++;
            if (batchCount >= MAX_BATCH_COUNT) {
                throw new IllegalStateException(
                        "Fuel prices pagination exceeded maximum batch count " + MAX_BATCH_COUNT
                );
            }
        }

        log.info(
                "Fetched all fuel prices batches for retailer={}: totalStations={}, batchCount={}",
                retailerName,
                allFuelPricesStations.size(),
                batchCount
        );

        return new PagedFetchResult<>(allFuelPricesStations, batchCount);
    }

    private PfsStationProcessingSummary normalizeAndUpsertStations(RetailerEntity retailer, List<PfsStationDto> pfsStations) {
        int normalizedStations = 0;
        int stationUpserts = 0;
        int skippedStations = 0;
        int skippedMissingSiteId = 0;

        for (PfsStationDto dto : pfsStations) {
            NormalizedStation normalizedStation = pfsStationNormalizer.normalize(dto);

            if (normalizedStation.getSiteId() == null || normalizedStation.getSiteId().isBlank()) {
                skippedStations++;
                skippedMissingSiteId++;
                log.warn(
                        "Skipping PFS station without site id: retailer={}, tradingName={}, brandName={}",
                        retailer.getName(),
                        dto.tradingName(),
                        dto.brandName()
                );
                continue;
            }

            normalizedStations++;
            stationUpserts += stationUpsertService.upsert(retailer, normalizedStation);
        }

        if (skippedStations > 0) {
            log.warn(
                    "Skipped PFS stations without site id: retailer={}, skippedStations={}",
                    retailer.getName(),
                    skippedMissingSiteId
            );
        }

        return PfsStationProcessingSummary.builder()
                .rawStationCount(pfsStations.size())
                .normalizedStationCount(normalizedStations)
                .skippedCount(skippedStations)
                .skippedMissingSiteIdCount(skippedMissingSiteId)
                .stationUpsertCount(stationUpserts)
                .build();
    }

    private void logReconciliationSummary(
            String retailerName,
            RawFeedFetchEntity pfsRawFeed,
            RawFeedFetchEntity fuelPricesRawFeed,
            PfsStationProcessingSummary pfsSummary,
            FuelPriceProcessingSummary fuelPriceSummary,
            ReconciliationDecision decision
    ) {
        String logMessage = "Ingestion reconciliation summary: retailer={}, pfsRawFeedFetchId={}, fuelPricesRawFeedFetchId={}, pfsRawStationCount={}, pfsNormalizedStationCount={}, pfsSkippedCount={}, pfsSkippedMissingSiteIdCount={}, pfsStationUpsertCount={}, fuelPriceRawStationCount={}, fuelPriceRawEntryCount={}, fuelPriceNormalizedObservationCount={}, fuelPriceSkippedInvalidUnusableEntryCount={}, fuelPriceInsertedCount={}, fuelPriceDuplicateCount={}, fuelPriceMissingStationCount={}, fuelPriceOtherPersistenceSkipCount={}, status={}, action={}, shouldAbort={}, message={}";

        if (decision.status() == ReconciliationStatus.FAILED) {
            log.warn(
                    logMessage,
                    retailerName,
                    pfsRawFeed.getId(),
                    fuelPricesRawFeed.getId(),
                    pfsSummary.rawStationCount(),
                    pfsSummary.normalizedStationCount(),
                    pfsSummary.skippedCount(),
                    pfsSummary.skippedMissingSiteIdCount(),
                    pfsSummary.stationUpsertCount(),
                    fuelPriceSummary.rawStationCount(),
                    fuelPriceSummary.rawFuelPriceEntryCount(),
                    fuelPriceSummary.normalizedObservationCount(),
                    fuelPriceSummary.skippedInvalidUnusableEntryCount(),
                    fuelPriceSummary.insertedCount(),
                    fuelPriceSummary.duplicateCount(),
                    fuelPriceSummary.missingStationCount(),
                    fuelPriceSummary.otherPersistenceSkipCount(),
                    decision.status(),
                    decision.action(),
                    decision.shouldAbort(),
                    decision.message()
            );
            return;
        }

        log.info(
                logMessage,
                retailerName,
                pfsRawFeed.getId(),
                fuelPricesRawFeed.getId(),
                pfsSummary.rawStationCount(),
                pfsSummary.normalizedStationCount(),
                pfsSummary.skippedCount(),
                pfsSummary.skippedMissingSiteIdCount(),
                pfsSummary.stationUpsertCount(),
                fuelPriceSummary.rawStationCount(),
                fuelPriceSummary.rawFuelPriceEntryCount(),
                fuelPriceSummary.normalizedObservationCount(),
                fuelPriceSummary.skippedInvalidUnusableEntryCount(),
                fuelPriceSummary.insertedCount(),
                fuelPriceSummary.duplicateCount(),
                fuelPriceSummary.missingStationCount(),
                fuelPriceSummary.otherPersistenceSkipCount(),
                decision.status(),
                decision.action(),
                decision.shouldAbort(),
                decision.message()
        );
    }

    private record PagedFetchResult<T>(
            List<T> records,
            int batchCount
    ) {
    }
}
