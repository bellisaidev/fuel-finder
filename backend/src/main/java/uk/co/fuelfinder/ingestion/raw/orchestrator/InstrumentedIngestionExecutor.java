package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.observability.FuelFinderIngestionMetrics;
import uk.co.fuelfinder.observability.FuelFinderIngestionMetrics.IngestionAttempt;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.repository.RetailerRepository;

@Service
@RequiredArgsConstructor
public class InstrumentedIngestionExecutor {

    private final RetailerRepository retailerRepository;
    private final RetailerIngestionService retailerIngestionService;
    private final FuelFinderIngestionMetrics ingestionMetrics;

    public RawIngestionSummary execute(String retailerName) {
        IngestionAttempt attempt = ingestionMetrics.startAttempt();
        RawIngestionSummary summary;

        try {
            RetailerEntity retailer = retailerRepository.findByName(retailerName)
                    .orElseThrow(() -> new FuelFinderIntegrationException(
                            "Retailer not found for ingestion: " + retailerName
                    ));
            summary = retailerIngestionService.ingest(retailer);
        } catch (RuntimeException | Error failure) {
            ingestionMetrics.recordFailure(attempt);
            throw failure;
        }

        ingestionMetrics.recordCompletion(attempt, summary);
        return summary;
    }
}
