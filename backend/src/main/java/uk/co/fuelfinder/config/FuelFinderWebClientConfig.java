package uk.co.fuelfinder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

@Configuration
public class FuelFinderWebClientConfig {

    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024; // 16 MB

    @Bean
    public WebClient fuelFinderAuthWebClient(WebClient.Builder builder, FuelFinderApiProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .exchangeStrategies(exchangeStrategies())
                .build();
    }

    @Bean
    public WebClient fuelFinderApiWebClient(WebClient.Builder builder, FuelFinderApiProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .exchangeStrategies(exchangeStrategies())
                .build();
    }

    private ExchangeStrategies exchangeStrategies() {
        return ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }
}