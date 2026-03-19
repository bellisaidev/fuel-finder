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
        var retailer = retailerRepository.findByName("SHELL")
                .orElseThrow(() -> new IllegalStateException("Retailer SHELL not found"));

        var summary = service.ingest(retailer);
        log.info("Ingestion done: {}", summary);
    }
}
