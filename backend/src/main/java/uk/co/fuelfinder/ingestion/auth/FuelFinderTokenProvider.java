package uk.co.fuelfinder.ingestion.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
            log.info("Fuel Finder token cache miss/expired, requesting new token");

            TokenResponse tokenResponse = oAuthTokenClient.generateAccessToken();

            if (tokenResponse == null || tokenResponse.data() == null || tokenResponse.data().access_token() == null) {
                throw new IllegalStateException("Failed to obtain access token from Fuel Finder OAuth API");
            }

            this.accessToken = tokenResponse.data().access_token();
            this.expiresAt = Instant.now().plusSeconds(tokenResponse.data().expires_in());

            log.info("Fuel Finder token cached successfully, expiresAt={}", this.expiresAt);
        } else {
            log.info("Fuel Finder token cache hit, expiresAt={}", this.expiresAt);
        }

        return accessToken;
    }
}