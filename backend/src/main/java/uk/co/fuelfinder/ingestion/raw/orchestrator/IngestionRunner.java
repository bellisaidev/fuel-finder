package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.repository.RetailerRepository;

@Slf4j
@Component
@Profile("local-manual")
public class IngestionRunner implements CommandLineRunner {

    private static final String FUEL_FINDER_RETAILER_NAME = "FUEL_FINDER_API";

    private final RetailerRepository retailerRepository;
    private final RetailerIngestionService retailerIngestionService;

    public IngestionRunner(
            RetailerRepository retailerRepository,
            RetailerIngestionService retailerIngestionService
    ) {
        this.retailerRepository = retailerRepository;
        this.retailerIngestionService = retailerIngestionService;
    }

    @Override
    public void run(String... args) {
        try {
            RetailerEntity retailer = retailerRepository.findByName(FUEL_FINDER_RETAILER_NAME)
                    .orElseThrow(() -> new FuelFinderIntegrationException(
                            "Retailer not found: " + FUEL_FINDER_RETAILER_NAME
                    ));

            RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

            log.info(
                    "Raw ingestion finished: success={}, retailer={}, pfsBatchNumber={}, pfsRecordCount={}, pfsRawFeedFetchId={}, fuelPricesBatchNumber={}, fuelPricesRecordCount={}, fuelPricesRawFeedFetchId={}, failureReason={}",
                    summary.isSuccess(),
                    summary.getRetailerName(),
                    summary.getPfsBatchNumber(),
                    summary.getPfsRecordCount(),
                    summary.getPfsRawFeedFetchId(),
                    summary.getFuelPricesBatchNumber(),
                    summary.getFuelPricesRecordCount(),
                    summary.getFuelPricesRawFeedFetchId(),
                    summary.getFailureReason()
            );

        } catch (FuelFinderConnectivityException e) {
            log.warn("Fuel Finder connectivity issue: {}. Is VPN enabled?", e.getMessage());

        } catch (FuelFinderAuthenticationException e) {
            log.error("Fuel Finder authentication failed: {}", e.getMessage());

        } catch (FuelFinderIntegrationException e) {
            log.error("Fuel Finder integration failed: {}", e.getMessage(), e);
        }
    }
}