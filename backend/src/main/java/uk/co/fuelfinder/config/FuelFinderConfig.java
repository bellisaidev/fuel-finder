package uk.co.fuelfinder.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import uk.co.fuelfinder.ingestion.auth.FuelFinderApiProperties;

@Configuration
@EnableConfigurationProperties(FuelFinderApiProperties.class)
public class FuelFinderConfig {
}