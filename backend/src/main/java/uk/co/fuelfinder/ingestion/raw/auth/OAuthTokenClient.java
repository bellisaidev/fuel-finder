package uk.co.fuelfinder.ingestion.raw.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpExceptionMapper;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpResilience;

import java.util.Map;

@Slf4j
@Component
public class OAuthTokenClient {

    private final WebClient fuelFinderAuthWebClient;
    private final FuelFinderApiProperties properties;
    private final ObjectMapper objectMapper;
    private final FuelFinderHttpResilience resilience;
    private final FuelFinderHttpExceptionMapper exceptionMapper;

    public OAuthTokenClient(
            @Qualifier("fuelFinderAuthWebClient") WebClient fuelFinderAuthWebClient,
            FuelFinderApiProperties properties,
            ObjectMapper objectMapper,
            FuelFinderHttpResilience resilience,
            FuelFinderHttpExceptionMapper exceptionMapper
    ) {
        this.fuelFinderAuthWebClient = fuelFinderAuthWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.resilience = resilience;
        this.exceptionMapper = exceptionMapper;
    }

    public TokenResponse generateAccessToken() {
        TokenRequest request = new TokenRequest(
                properties.getOauth().getClientId().trim(),
                properties.getOauth().getClientSecret().trim()
        );

        logSafeRequest(request);

        try {
            TokenResponse response = resilience.execute(fuelFinderAuthWebClient.post()
                    .uri(properties.getOauth().getTokenPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TokenResponse.class))
                    .block();

            validateTokenResponse(response);
            log.info("Fuel Finder token acquired successfully: tokenType={}, expiresIn={}",
                    response.data().token_type(),
                    response.data().expires_in());

            return response;

        } catch (RuntimeException e) {
            throw exceptionMapper.mapOAuthFailure(e);
        }
    }

    private void validateTokenResponse(TokenResponse response) {
        if (response == null) {
            throw new FuelFinderInvalidResponseException("Fuel Finder token response was null");
        }
        if (response.data() == null) {
            throw new FuelFinderInvalidResponseException("Fuel Finder token response.data was null");
        }
        if (response.data().access_token() == null || response.data().access_token().isBlank()) {
            throw new FuelFinderInvalidResponseException("Fuel Finder token response.access_token was missing");
        }
        if (response.data().expires_in() <= 0) {
            throw new FuelFinderInvalidResponseException("Fuel Finder token response.expires_in was invalid");
        }
    }

    private void logSafeRequest(TokenRequest request) {
        try {
            String safeBody = objectMapper.writeValueAsString(Map.of(
                    "client_id", request.client_id(),
                    "client_secret", "[REDACTED]"
            ));
            log.info("Fuel Finder token request url={}{}",
                    properties.getBaseUrl(),
                    properties.getOauth().getTokenPath());
        } catch (Exception e) {
            log.warn("Unable to serialize safe Fuel Finder token request body", e);
        }
    }
}
