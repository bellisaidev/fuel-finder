package uk.co.fuelfinder.ingestion.raw.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpExceptionMapper;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpResilience;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthTokenClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateAccessTokenReturnsParsedResponse() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        OAuthTokenClient client = newClient(
                webClient(request -> {
                    capturedRequest.set(request);
                    return jsonResponse(HttpStatus.OK, """
                            {
                              "success": true,
                              "data": {
                                "access_token": "abc123",
                                "token_type": "Bearer",
                                "expires_in": 3600,
                                "refresh_token": "refresh-1"
                              },
                              "message": "ok"
                            }
                            """);
                }),
                properties(),
                objectMapper
        );

        TokenResponse response = client.generateAccessToken();

        assertThat(response.data().access_token()).isEqualTo("abc123");
        assertThat(response.data().expires_in()).isEqualTo(3600);
        assertThat(capturedRequest.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(capturedRequest.get().url().getPath()).isEqualTo("/oauth/token");
        assertThat(capturedRequest.get().headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void generateAccessTokenThrowsAuthenticationExceptionForClientError() {
        OAuthTokenClient client = newClient(
                webClient(request -> jsonResponse(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_client\"}")),
                properties(),
                objectMapper
        );

        assertThatThrownBy(client::generateAccessToken)
                .isInstanceOf(FuelFinderAuthenticationException.class)
                .hasMessageContaining("failed authentication")
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    @Test
    void generateAccessTokenThrowsConnectivityExceptionForRequestFailure() {
        OAuthTokenClient client = newClient(
                webClient(request -> Mono.error(new WebClientRequestException(
                        new IOException("connection refused"),
                        HttpMethod.POST,
                        URI.create("https://example.test/oauth/token"),
                        HttpHeaders.EMPTY
                ))),
                properties(),
                objectMapper
        );

        assertThatThrownBy(client::generateAccessToken)
                .isInstanceOf(FuelFinderConnectivityException.class)
                .hasMessageContaining("connectivity issue");
    }

    @Test
    void generateAccessTokenThrowsIntegrationExceptionForServerError() {
        OAuthTokenClient client = newClient(
                webClient(request -> jsonResponse(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"unavailable\"}")),
                properties(),
                objectMapper
        );

        assertThatThrownBy(client::generateAccessToken)
                .isInstanceOf(FuelFinderIntegrationException.class)
                .hasMessageContaining("Fuel Finder token request failed")
                .hasMessageContaining("503 SERVICE_UNAVAILABLE");
    }

    @Test
    void generateAccessTokenThrowsInvalidResponseExceptionWhenBodyIsNull() {
        OAuthTokenClient client = newClient(
                webClient(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build())),
                properties(),
                objectMapper
        );

        assertThatThrownBy(client::generateAccessToken)
                .isInstanceOf(FuelFinderInvalidResponseException.class)
                .hasMessageContaining("response was null");
    }

    @Test
    void generateAccessTokenThrowsInvalidResponseExceptionWhenTokenMissing() {
        OAuthTokenClient client = newClient(
                webClient(request -> jsonResponse(HttpStatus.OK, """
                        {
                          "success": true,
                          "data": {
                            "access_token": " ",
                            "token_type": "Bearer",
                            "expires_in": 3600,
                            "refresh_token": "refresh-1"
                          },
                          "message": "ok"
                        }
                        """)),
                properties(),
                objectMapper
        );

        assertThatThrownBy(client::generateAccessToken)
                .isInstanceOf(FuelFinderInvalidResponseException.class)
                .hasMessageContaining("access_token was missing");
    }

    @Test
    void generateAccessTokenThrowsInvalidResponseExceptionWhenExpiryIsInvalid() {
        OAuthTokenClient client = newClient(
                webClient(request -> jsonResponse(HttpStatus.OK, """
                        {
                          "success": true,
                          "data": {
                            "access_token": "abc123",
                            "token_type": "Bearer",
                            "expires_in": 0,
                            "refresh_token": "refresh-1"
                          },
                          "message": "ok"
                        }
                        """)),
                properties(),
                objectMapper
        );

        assertThatThrownBy(client::generateAccessToken)
                .isInstanceOf(FuelFinderInvalidResponseException.class)
                .hasMessageContaining("expires_in was invalid");
    }

    @Test
    void generateAccessTokenWrapsUnexpectedFailure() {
        OAuthTokenClient client = newClient(
                webClient(request -> Mono.error(new IllegalStateException("boom"))),
                properties(),
                objectMapper
        );

        assertThatThrownBy(client::generateAccessToken)
                .isInstanceOf(FuelFinderIntegrationException.class)
                .hasMessageContaining("Unexpected error during Fuel Finder token acquisition")
                .hasRootCauseMessage("boom");
    }

    private static FuelFinderApiProperties properties() {
        FuelFinderApiProperties properties = new FuelFinderApiProperties();
        properties.setBaseUrl("https://example.test");

        FuelFinderApiProperties.OAuth oauth = new FuelFinderApiProperties.OAuth();
        oauth.setClientId(" client-id ");
        oauth.setClientSecret(" secret ");
        oauth.setTokenPath("/oauth/token");
        properties.setOauth(oauth);
        return properties;
    }

    private static WebClient webClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder().exchangeFunction(exchangeFunction).build();
    }

    @Test
    void generateAccessTokenRetriesTransientServerFailureAndRecovers() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderApiProperties properties = properties();
        properties.getHttp().getRetry().setMaxRetries(1);
        properties.getHttp().getRetry().setInitialBackoff(Duration.ofMillis(1));
        properties.getHttp().getRetry().setMaxBackoff(Duration.ofMillis(1));
        properties.getHttp().getRetry().setJitter(0);
        OAuthTokenClient client = newClientWithConfiguredRetry(
                webClient(request -> attempts.incrementAndGet() == 1
                        ? jsonResponse(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"unavailable\"}")
                        : jsonResponse(HttpStatus.OK, """
                                {
                                  "data": {
                                    "access_token": "recovered",
                                    "token_type": "Bearer",
                                    "expires_in": 3600
                                  }
                                }
                                """)),
                properties,
                objectMapper
        );

        assertThat(client.generateAccessToken().data().access_token()).isEqualTo("recovered");
        assertThat(attempts).hasValue(2);
    }

    private static OAuthTokenClient newClient(
            WebClient webClient,
            FuelFinderApiProperties properties,
            ObjectMapper objectMapper
    ) {
        properties.getHttp().getRetry().setMaxRetries(0);
        return newClientWithConfiguredRetry(webClient, properties, objectMapper);
    }

    private static OAuthTokenClient newClientWithConfiguredRetry(
            WebClient webClient,
            FuelFinderApiProperties properties,
            ObjectMapper objectMapper
    ) {
        FuelFinderHttpResilience resilience = new FuelFinderHttpResilience(properties);
        return new OAuthTokenClient(
                webClient,
                properties,
                objectMapper,
                resilience,
                new FuelFinderHttpExceptionMapper(resilience, objectMapper)
        );
    }

    private static Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
