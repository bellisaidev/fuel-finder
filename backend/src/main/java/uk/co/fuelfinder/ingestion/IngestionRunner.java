package uk.co.fuelfinder.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.db.repo.RetailerRepository;

@Slf4j
@Component
@Profile("local")
public class IngestionRunner implements CommandLineRunner {

    private final RetailerRepository retailerRepository;
    private final RetailerIngestionService service;

    public IngestionRunner(RetailerRepository retailerRepository, RetailerIngestionService service) {
        this.retailerRepository = retailerRepository;
        this.service = service;
    }

    @Override
    public void run(String... args) {
        var source = retailerRepository.findByName("FUEL_FINDER_API")
                .orElseThrow(() -> new IllegalStateException("Source FUEL_FINDER_API not found"));

        var summary = service.ingest(source);
        log.info("Ingestion done: {}", summary);
    }
}