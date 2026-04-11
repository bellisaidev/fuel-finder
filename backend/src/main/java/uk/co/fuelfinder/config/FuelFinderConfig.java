package uk.co.fuelfinder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;
import uk.co.fuelfinder.ingestion.raw.orchestrator.IngestionSchedulerProperties;

@Configuration
@EnableConfigurationProperties({
        FuelFinderApiProperties.class,
        IngestionSchedulerProperties.class
})
public class FuelFinderConfig {
}