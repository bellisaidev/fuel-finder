package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fuelfinder.ingestion.scheduler", name = "enabled", havingValue = "true")
public class IngestionScheduler {

    private final IngestionSchedulerProperties schedulerProperties;
    private final InstrumentedIngestionExecutor ingestionExecutor;

    @Scheduled(cron = "${fuelfinder.ingestion.scheduler.cron:0 */30 * * * *}")
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

        RawIngestionSummary summary = ingestionExecutor.execute(retailerName);

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
