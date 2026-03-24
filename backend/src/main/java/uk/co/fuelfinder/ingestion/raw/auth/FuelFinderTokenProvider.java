package uk.co.fuelfinder.ingestion.raw.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;

import java.time.Instant;

@Slf4j
@Component
public class FuelFinderTokenProvider {

    private final OAuthTokenClient oAuthTokenClient;

    private String accessToken;
    private Instant expiresAt;

    public FuelFinderTokenProvider(OAuthTokenClient oAuthTokenClient) {
        this.oAuthTokenClient = oAuthTokenClient;
    }

    public synchronized String getAccessToken() {
        if (accessToken == null || expiresAt == null || Instant.now().isAfter(expiresAt.minusSeconds(30))) {
            log.info("Fuel Finder token cache miss or expired, requesting new token");

            try {
                TokenResponse tokenResponse = oAuthTokenClient.generateAccessToken();

                this.accessToken = tokenResponse.data().access_token();
                this.expiresAt = Instant.now().plusSeconds(tokenResponse.data().expires_in());

                log.info("Fuel Finder token cached successfully, expiresAt={}", this.expiresAt);
            } catch (FuelFinderIntegrationException e) {
                log.error("Unable to obtain Fuel Finder access token: {}", e.getMessage());
                throw e;
            }
        } else {
            log.info("Fuel Finder token cache hit, expiresAt={}", this.expiresAt);
        }

        return accessToken;
    }
}