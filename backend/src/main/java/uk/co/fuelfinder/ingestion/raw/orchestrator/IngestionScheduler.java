package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.repository.RetailerRepository;

@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final IngestionSchedulerProperties schedulerProperties;
    private final RetailerRepository retailerRepository;
    private final RetailerIngestionService retailerIngestionService;

    @Scheduled(cron = "${fuelfinder.ingestion.scheduler.cron}")
    @SchedulerLock(name = "fuelFinderIngestionJob")
    public void runScheduledIngestion() {
        if (!schedulerProperties.isEnabled()) {
            return;
        }

        String retailerName = schedulerProperties.getRetailerName();
        RetailerEntity retailer = retailerRepository.findByName(retailerName)
                .orElseThrow(() -> new FuelFinderIntegrationException(
                        "Retailer not found for scheduled ingestion: " + retailerName
                ));

        retailerIngestionService.ingest(retailer);
    }
}
