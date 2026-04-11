package uk.co.fuelfinder.ingestion.raw.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;

import java.util.List;
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
        FuelFinderPfsClient client = new FuelFinderPfsClient(
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
    void fetchBatchReturnsEmptyListForEmptyResponseArray() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = new FuelFinderPfsClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.OK, "[]"))
                        .build(),
                tokenProvider
        );

        assertThat(client.fetchBatch(1)).isEmpty();
    }

    @Test
    void fetchBatchThrowsWhenResponseBodyIsEmpty() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = new FuelFinderPfsClient(
                WebClient.builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                        .build(),
                tokenProvider
        );

        assertThatThrownBy(() -> client.fetchBatch(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Fuel Finder PFS response was null");
    }

    @Test
    void fetchBatchThrowsWhenHttpErrorOccurs() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-123");

        FuelFinderPfsClient client = new FuelFinderPfsClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.BAD_GATEWAY, "{\"error\":\"upstream\"}"))
                        .build(),
                tokenProvider
        );

        assertThatThrownBy(() -> client.fetchBatch(3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fuel Finder PFS request failed")
                .hasMessageContaining("502 BAD_GATEWAY");
    }

    private static Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
