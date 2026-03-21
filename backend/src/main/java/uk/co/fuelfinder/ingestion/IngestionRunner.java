package uk.co.fuelfinder.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.auth.FuelFinderApiProperties;

@Slf4j
@Component
@Profile("local")
public class IngestionRunner implements CommandLineRunner {

    private final FuelFinderApiProperties properties;
    private final FuelFinderTokenProvider tokenProvider;

    public IngestionRunner(
            FuelFinderApiProperties properties,
            FuelFinderTokenProvider tokenProvider
    ) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void run(String... args) {
        log.info("Fuel Finder baseUrl={}", properties.getBaseUrl());
        log.info("Fuel Finder tokenPath={}", properties.getOauth().getTokenPath());
        log.info("Fuel Finder clientIdPresent={}", properties.getOauth().getClientId() != null);

        String accessToken = tokenProvider.getAccessToken();

        log.info("Fuel Finder access token acquired successfully: present={}",
                accessToken != null && !accessToken.isBlank());
        log.info("Fuel Finder access token length={}", accessToken != null ? accessToken.length() : 0);
    }
}