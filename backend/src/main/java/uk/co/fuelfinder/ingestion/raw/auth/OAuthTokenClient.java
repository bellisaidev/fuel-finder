package uk.co.fuelfinder.ingestion.raw.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;

import java.util.Map;

@Slf4j
@Component
public class OAuthTokenClient {

    private final WebClient fuelFinderAuthWebClient;
    private final FuelFinderApiProperties properties;
    private final ObjectMapper objectMapper;

    public OAuthTokenClient(
            @Qualifier("fuelFinderAuthWebClient") WebClient fuelFinderAuthWebClient,
            FuelFinderApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.fuelFinderAuthWebClient = fuelFinderAuthWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TokenResponse generateAccessToken() {
        TokenRequest request = new TokenRequest(
                properties.getOauth().getClientId().trim(),
                properties.getOauth().getClientSecret().trim()
        );

        logSafeRequest(request);

        try {
            TokenResponse response = fuelFinderAuthWebClient.post()
                    .uri(properties.getOauth().getTokenPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new FuelFinderAuthenticationException(
                                            "Fuel Finder token request failed with client error: status="
                                                    + clientResponse.statusCode() + ", body=" + body))
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .map(body -> new FuelFinderIntegrationException(
                                            "Fuel Finder token request failed with server error: status="
                                                    + clientResponse.statusCode() + ", body=" + body))
                    )
                    .bodyToMono(TokenResponse.class)
                    .block();

            validateTokenResponse(response);
            log.info("Fuel Finder token acquired successfully: tokenType={}, expiresIn={}",
                    response.data().token_type(),
                    response.data().expires_in());

            return response;

        } catch (FuelFinderAuthenticationException e) {
            throw e;

        } catch (WebClientRequestException e) {
            throw new FuelFinderConnectivityException(
                    "Fuel Finder token request failed due to connectivity issue. " +
                            "Check VPN, DNS, proxy, firewall, or remote host availability.",
                    e
            );

        } catch (WebClientResponseException e) {
            throw new FuelFinderIntegrationException(
                    "Fuel Finder token request failed: status=" + e.getStatusCode() +
                            ", responseBody=" + e.getResponseBodyAsString(),
                    e
            );

        } catch (FuelFinderInvalidResponseException e) {
            throw e;

        } catch (Exception e) {
            throw new FuelFinderIntegrationException("Unexpected error during Fuel Finder token acquisition", e);
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