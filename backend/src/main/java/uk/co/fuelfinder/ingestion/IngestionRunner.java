package uk.co.fuelfinder.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.auth.FuelFinderApiProperties;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.fetch.FuelFinderPfsClient;
import uk.co.fuelfinder.ingestion.fetch.PfsStationDto;

import java.util.List;

@Slf4j
@Component
@Profile("local")
public class IngestionRunner implements CommandLineRunner {

    private final FuelFinderApiProperties properties;
    private final FuelFinderTokenProvider tokenProvider;
    private final FuelFinderPfsClient pfsClient;

    public IngestionRunner(
            FuelFinderApiProperties properties,
            FuelFinderTokenProvider tokenProvider,
            FuelFinderPfsClient pfsClient
    ) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.pfsClient = pfsClient;
    }

    @Override
    public void run(String... args) {
        log.info("Fuel Finder baseUrl={}", properties.getBaseUrl());
        log.info("Fuel Finder tokenPath={}", properties.getOauth().getTokenPath());
        log.info("Fuel Finder clientIdPresent={}", properties.getOauth().getClientId() != null);

        try {
            String accessToken = tokenProvider.getAccessToken();
            log.info("Fuel Finder access token acquired successfully: present={}",
                    accessToken != null && !accessToken.isBlank());

            List<PfsStationDto> stations = pfsClient.fetchBatch(1);
            log.info("Fuel Finder PFS stations fetched: {}", stations.size());

            if (!stations.isEmpty()) {
                PfsStationDto first = stations.get(0);
                log.info("Fuel Finder sample station: nodeId={}, tradingName={}, brandName={}",
                        first.nodeId(),
                        first.tradingName(),
                        first.brandName());
            }

        } catch (FuelFinderConnectivityException e) {
            log.warn("Fuel Finder connectivity issue: {}. Is VPN enabled?", e.getMessage());

        } catch (FuelFinderAuthenticationException e) {
            log.error("Fuel Finder authentication failed: {}", e.getMessage());

        } catch (FuelFinderIntegrationException e) {
            log.error("Fuel Finder integration failed: {}", e.getMessage(), e);
        }
    }
}