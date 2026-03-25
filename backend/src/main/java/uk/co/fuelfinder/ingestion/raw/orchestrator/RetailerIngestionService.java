package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.raw.FeedType;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderFuelPricesClient;
import uk.co.fuelfinder.ingestion.raw.client.FuelFinderPfsClient;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;
import uk.co.fuelfinder.ingestion.raw.writer.RawFeedStorageService;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetailerIngestionService {

    private static final int DEFAULT_BATCH_NUMBER = 1;
    private static final String PFS_ENDPOINT_PATH = "/pfs";
    private static final String FUEL_PRICES_ENDPOINT_PATH = "/pfs/fuel-prices";

    private final FuelFinderPfsClient pfsClient;
    private final FuelFinderFuelPricesClient fuelPricesClient;
    private final RawFeedStorageService rawFeedStorageService;

    public RawIngestionSummary ingest(RetailerEntity retailer) {
        Instant startedAt = Instant.now();
        String retailerName = retailer.getName();

        log.info("Starting raw ingestion for retailer={}", retailerName);

        try {
            int pfsBatchNumber = DEFAULT_BATCH_NUMBER;
            List<PfsStationDto> pfsStations = pfsClient.fetchBatch(pfsBatchNumber);

            log.info(
                    "Fetched PFS batch {} for retailer={}: {} stations",
                    pfsBatchNumber,
                    retailerName,
                    pfsStations.size()
            );

            RawFeedFetchEntity pfsRawFeed = rawFeedStorageService.store(
                    retailer,
                    FeedType.PFS,
                    PFS_ENDPOINT_PATH,
                    pfsBatchNumber,
                    pfsStations,
                    pfsStations.size()
            );

            int fuelPricesBatchNumber = DEFAULT_BATCH_NUMBER;
            List<FuelPricesStationDto> fuelPricesStations = fuelPricesClient.fetchFuelPrices(fuelPricesBatchNumber);

            log.info(
                    "Fetched fuel prices batch {} for retailer={}: {} stations",
                    fuelPricesBatchNumber,
                    retailerName,
                    fuelPricesStations.size()
            );

            RawFeedFetchEntity fuelPricesRawFeed = rawFeedStorageService.store(
                    retailer,
                    FeedType.FUEL_PRICES,
                    FUEL_PRICES_ENDPOINT_PATH,
                    fuelPricesBatchNumber,
                    fuelPricesStations,
                    fuelPricesStations.size()
            );

            RawIngestionSummary summary = RawIngestionSummary.success(
                    retailerName,
                    startedAt,
                    pfsBatchNumber,
                    pfsStations.size(),
                    pfsRawFeed.getId(),
                    fuelPricesBatchNumber,
                    fuelPricesStations.size(),
                    fuelPricesRawFeed.getId()
            );

            log.info(
                    "Completed raw ingestion for retailer={}: pfsRecordCount={}, fuelPricesRecordCount={}, pfsRawFeedFetchId={}, fuelPricesRawFeedFetchId={}",
                    retailerName,
                    summary.getPfsRecordCount(),
                    summary.getFuelPricesRecordCount(),
                    summary.getPfsRawFeedFetchId(),
                    summary.getFuelPricesRawFeedFetchId()
            );

            return summary;

        } catch (Exception e) {
            log.error("Raw ingestion failed for retailer={}: {}", retailerName, e.getMessage(), e);

            return RawIngestionSummary.failed(
                    retailerName,
                    startedAt,
                    e.getMessage() == null ? "Unknown ingestion error" : e.getMessage()
            );
        }
    }
}