package uk.co.fuelfinder.ingestion.raw.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FuelFinderFuelPricesClientTest {

    @Test
    void fetchFuelPricesReturnsParsedResponseAndUsesBearerToken() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-456");

        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        FuelFinderFuelPricesClient client = new FuelFinderFuelPricesClient(
                WebClient.builder()
                        .exchangeFunction(request -> {
                            capturedRequest.set(request);
                            return jsonResponse(HttpStatus.OK, """
                                    [
                                      {
                                        "node_id": "site-9",
                                        "trading_name": "BP Victoria",
                                        "fuel_prices": [
                                          {
                                            "fuel_type": "E10",
                                            "price": 1.459
                                          }
                                        ]
                                      }
                                    ]
                                    """);
                        })
                        .build(),
                tokenProvider
        );

        List<FuelPricesStationDto> stations = client.fetchFuelPrices(4);

        assertThat(stations).hasSize(1);
        assertThat(stations.getFirst().nodeId()).isEqualTo("site-9");
        assertThat(stations.getFirst().fuelPrices()).hasSize(1);
        assertThat(capturedRequest.get().url().getPath()).isEqualTo("/pfs/fuel-prices");
        assertThat(capturedRequest.get().url().getQuery()).isEqualTo("batch-number=4");
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-456");
    }

    @Test
    void fetchFuelPricesReturnsEmptyListForEmptyArray() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-456");

        FuelFinderFuelPricesClient client = new FuelFinderFuelPricesClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.OK, "[]"))
                        .build(),
                tokenProvider
        );

        assertThat(client.fetchFuelPrices(2)).isEmpty();
    }

    @Test
    void fetchFuelPricesThrowsInvalidResponseWhenBodyIsEmpty() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-456");

        FuelFinderFuelPricesClient client = new FuelFinderFuelPricesClient(
                WebClient.builder()
                        .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                        .build(),
                tokenProvider
        );

        assertThatThrownBy(() -> client.fetchFuelPrices(5))
                .isInstanceOf(FuelFinderInvalidResponseException.class)
                .hasMessageContaining("response is null for batch 5");
    }

    @Test
    void fetchFuelPricesWrapsHttpErrors() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-456");

        FuelFinderFuelPricesClient client = new FuelFinderFuelPricesClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"down\"}"))
                        .build(),
                tokenProvider
        );

        assertThatThrownBy(() -> client.fetchFuelPrices(6))
                .isInstanceOf(FuelFinderIntegrationException.class)
                .hasMessageContaining("Unexpected error while fetching Fuel Finder fuel prices batch 6");
    }

    @Test
    void fetchFuelPricesWrapsJsonParsingFailures() {
        FuelFinderTokenProvider tokenProvider = mock(FuelFinderTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("token-456");

        FuelFinderFuelPricesClient client = new FuelFinderFuelPricesClient(
                WebClient.builder()
                        .exchangeFunction(request -> jsonResponse(HttpStatus.OK, "[{"))
                        .build(),
                tokenProvider
        );

        assertThatThrownBy(() -> client.fetchFuelPrices(8))
                .isInstanceOf(FuelFinderIntegrationException.class)
                .hasMessageContaining("Unexpected error while fetching Fuel Finder fuel prices batch 8");
    }

    private static Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
