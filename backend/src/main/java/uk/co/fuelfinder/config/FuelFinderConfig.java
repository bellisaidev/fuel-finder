package uk.co.fuelfinder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;
import uk.co.fuelfinder.ingestion.raw.orchestrator.IngestionSchedulerProperties;

@Configuration
@EnableCaching
@EnableConfigurationProperties({
        FuelFinderApiProperties.class,
        IngestionSchedulerProperties.class,
        StationQueryCacheProperties.class
})
public class FuelFinderConfig {
}
