package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;

@Slf4j
@Component
@Profile("local-manual")
public class IngestionRunner implements CommandLineRunner {

    private static final String FUEL_FINDER_RETAILER_NAME = "FUEL_FINDER_API";

    private final InstrumentedIngestionExecutor ingestionExecutor;

    public IngestionRunner(InstrumentedIngestionExecutor ingestionExecutor) {
        this.ingestionExecutor = ingestionExecutor;
    }

    @Override
    public void run(String... args) {
        try {
            RawIngestionSummary summary = ingestionExecutor.execute(FUEL_FINDER_RETAILER_NAME);

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
