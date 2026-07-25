package uk.co.fuelfinder.ingestion.raw.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpExceptionMapper;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpResilience;

import java.util.List;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FuelFinderPfsClientTest {

    @Test
    void fetchBatchReturnsParsedStationsAndUsesBearerToken() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        FuelFinderPfsClient client = newClient(
                WebClient.builder()
                        .exchangeFunction(request -> {
                            capturedRequest.set(request);
                            return jsonResponse(HttpStatus.OK, """
                                    [
                                      {
                                        "node_id": "site-1",
                                        "trading_name": "Shell Westminster",
                                        "brand_name": "Shell",
                                        "location": {
                                          "latitude": 51.5007,
                                          "longitude": -0.1246
                                        },
                                        "fuel_types": ["E10", "B7"]
                                      }
                                    ]
                                    """);
                        })
                        .build(),
                tokenProvider
        );

        List<PfsStationDto> stations = client.fetchBatch(7);

        assertThat(stations).hasSize(1);
        assertThat(stations.getFirst().nodeId()).isEqualTo("site-1");
        assertThat(stations.getFirst().location().latitude()).isEqualTo(51.5007);
        assertThat(capturedRequest.get().url().getPath()).isEqualTo("/pfs");
        assertThat(capturedRequest.get().url().getQuery()).isEqualTo("batch-number=7");
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-123");
    }

    @Test
    void fetchBatchAcceptsSiteIdAsStationIdentifierAlias() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = newClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.OK, """
                                [
                                  {
                                    "site_id": "site-10",
                                    "trading_name": "Alias Station"
                                  }
                                ]
                                """))
                        .build(),
                tokenProvider
        );

        List<PfsStationDto> stations = client.fetchBatch(1);

        assertThat(stations).hasSize(1);
        assertThat(stations.getFirst().nodeId()).isEqualTo("site-10");
    }

    @Test
    void fetchBatchReturnsEmptyListForEmptyResponseArray() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = newClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.OK, "[]"))
                        .build(),
                tokenProvider
        );

        assertThat(client.fetchBatch(1)).isEmpty();
    }

    @Test
    void fetchBatchReturnsEmptyListWhenRequestedBatchIsUnavailable() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = newClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.NOT_FOUND, """
                                {
                                  "data": {
                                    "data": {
                                      "message": "Requested batch 6 is not available"
                                    }
                                  }
                                }
                                """))
                        .build(),
                tokenProvider
        );

        assertThat(client.fetchBatch(6)).isEmpty();
    }

    @Test
    void batchUnavailableSentinelIsNotRetriedEvenOnTransientStatus() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    attempts.incrementAndGet();
                    return jsonResponse(HttpStatus.SERVICE_UNAVAILABLE, """
                            {"message":"Requested batch 6 is not available"}
                            """);
                })
                .build();
        FuelFinderApiProperties properties = new FuelFinderApiProperties();
        properties.getHttp().getRetry().setJitter(0);

        assertThat(newClient(webClient, tokenProvider, properties).fetchBatch(6)).isEmpty();
        assertThat(attempts).hasValue(1);
    }

    @Test
    void fetchBatchThrowsWhenResponseBodyIsEmpty() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = newClient(
                WebClient.builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                        .build(),
                tokenProvider
        );

        assertThatThrownBy(() -> client.fetchBatch(1))
                .isInstanceOf(FuelFinderInvalidResponseException.class)
                .hasMessage("Fuel Finder PFS response was null");
    }

    @Test
    void fetchBatchThrowsWhenHttpErrorOccurs() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = newClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.BAD_GATEWAY, "{\"error\":\"upstream\"}"))
                        .build(),
                tokenProvider
        );

        assertThatThrownBy(() -> client.fetchBatch(3))
                .isInstanceOf(FuelFinderIntegrationException.class)
                .hasMessageContaining("Fuel Finder PFS batch 3 request failed")
                .hasMessageContaining("502 BAD_GATEWAY");
    }

    private static Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    @Test
    void fetchBatchRetriesTransientServerFailureAndRecovers() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> attempts.incrementAndGet() == 1
                        ? jsonResponse(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"unavailable\"}")
                        : jsonResponse(HttpStatus.OK, "[]"))
                .build();

        FuelFinderApiProperties properties = new FuelFinderApiProperties();
        properties.getHttp().getRetry().setMaxRetries(1);
        properties.getHttp().getRetry().setInitialBackoff(Duration.ofMillis(1));
        properties.getHttp().getRetry().setMaxBackoff(Duration.ofMillis(1));
        properties.getHttp().getRetry().setJitter(0);

        assertThat(newClient(webClient, tokenProvider, properties).fetchBatch(2)).isEmpty();
        assertThat(attempts).hasValue(2);
    }

    private static FuelFinderPfsClient newClient(
            WebClient webClient,
            FuelFinderTokenProvider tokenProvider
    ) {
        FuelFinderApiProperties properties = new FuelFinderApiProperties();
        properties.getHttp().getRetry().setMaxRetries(0);
        return newClient(webClient, tokenProvider, properties);
    }

    private static FuelFinderPfsClient newClient(
            WebClient webClient,
            FuelFinderTokenProvider tokenProvider,
            FuelFinderApiProperties properties
    ) {
        FuelFinderHttpResilience resilience = new FuelFinderHttpResilience(properties);
        return new FuelFinderPfsClient(
                webClient,
                tokenProvider,
                resilience,
                new FuelFinderHttpExceptionMapper(resilience, new ObjectMapper())
        );
    }
}
