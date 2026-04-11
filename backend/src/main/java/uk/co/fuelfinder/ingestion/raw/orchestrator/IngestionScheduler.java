package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.repository.RetailerRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final IngestionSchedulerProperties schedulerProperties;
    private final RetailerRepository retailerRepository;
    private final RetailerIngestionService retailerIngestionService;

    @Scheduled(cron = "${fuelfinder.ingestion.scheduler.cron}")
    @SchedulerLock(
            name = "fuelFinderIngestionJob",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT1M"
    )
    public void runScheduledIngestion() {
        if (!schedulerProperties.isEnabled()) {
            log.debug("Fuel Finder scheduled ingestion is disabled");
            return;
        }

        String retailerName = schedulerProperties.getRetailerName();
        log.info("Starting scheduled ingestion for retailer={}", retailerName);

        RetailerEntity retailer = retailerRepository.findByName(retailerName)
                .orElseThrow(() -> new FuelFinderIntegrationException(
                        "Retailer not found for scheduled ingestion: " + retailerName
                ));

        RawIngestionSummary summary = retailerIngestionService.ingest(retailer);

        log.info(
                "Scheduled ingestion completed: success={}, retailer={}, pfsRecordCount={}, fuelPricesRecordCount={}, failureReason={}",
                summary.isSuccess(),
                summary.getRetailerName(),
                summary.getPfsRecordCount(),
                summary.getFuelPricesRecordCount(),
                summary.getFailureReason()
        );
    }
}
