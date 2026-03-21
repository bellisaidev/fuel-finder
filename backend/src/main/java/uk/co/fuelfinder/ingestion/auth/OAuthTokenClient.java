package uk.co.fuelfinder.ingestion.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Component
public class OAuthTokenClient {

    private final WebClient fuelFinderAuthWebClient;
    private final FuelFinderApiProperties properties;
    private final ObjectMapper objectMapper;

    public OAuthTokenClient(
            WebClient fuelFinderAuthWebClient,
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

        try {
            String safeBody = objectMapper.writeValueAsString(Map.of(
                    "client_id", request.client_id(),
                    "client_secret", "[REDACTED]"
            ));
            log.info("Fuel Finder token request body={}", safeBody);
            log.info("Fuel Finder token request url={}{}",
                    properties.getBaseUrl(),
                    properties.getOauth().getTokenPath());
        } catch (Exception e) {
            log.warn("Unable to serialize safe Fuel Finder token request body", e);
        }

        TokenResponse response = fuelFinderAuthWebClient.post()
                .uri(properties.getOauth().getTokenPath())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> new IllegalStateException(
                                        "Fuel Finder token request failed: status="
                                                + clientResponse.statusCode()
                                                + ", body=" + body))
                )
                .bodyToMono(TokenResponse.class)
                .block();

        if (response == null) {
            throw new IllegalStateException("Fuel Finder token response was null");
        }

        log.info("Fuel Finder token response success={}", response.success());
        log.info("Fuel Finder token response message={}", response.message());
        log.info("Fuel Finder token data present={}", response.data() != null);

        if (response.data() != null) {
            log.info("Fuel Finder token_type={}", response.data().token_type());
            log.info("Fuel Finder expires_in={}", response.data().expires_in());
            log.info("Fuel Finder access_token present={}",
                    response.data().access_token() != null && !response.data().access_token().isBlank());
            log.info("Fuel Finder refresh_token present={}",
                    response.data().refresh_token() != null && !response.data().refresh_token().isBlank());
        }

        return response;
    }
}