package uk.co.fuelfinder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import uk.co.fuelfinder.ingestion.auth.FuelFinderApiProperties;

@Configuration
public class FuelFinderWebClientConfig {

    @Bean
    public WebClient fuelFinderWebClient(WebClient.Builder builder, FuelFinderApiProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}