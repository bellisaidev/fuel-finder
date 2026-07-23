package uk.co.fuelfinder.ingestion.raw.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class FuelFinderHttpExceptionMapperTest {

    private final FuelFinderHttpExceptionMapper mapper = new FuelFinderHttpExceptionMapper(
            new FuelFinderHttpResilience(new FuelFinderApiProperties()),
            new ObjectMapper()
    );

    @Test
    void mapsApiUnauthorizedToAuthenticationFailure() {
        RuntimeException mapped = mapper.mapApiFailure("PFS request", response(401, "{}"));

        assertThat(mapped)
                .isInstanceOf(FuelFinderAuthenticationException.class)
                .hasMessageContaining("401");
    }

    @Test
    void mapsOAuthInvalidClientToAuthenticationFailure() {
        RuntimeException mapped = mapper.mapOAuthFailure(response(400, "{\"error\":\"invalid_client\"}"));

        assertThat(mapped)
                .isInstanceOf(FuelFinderAuthenticationException.class)
                .hasMessageContaining("400");
    }

    @Test
    void mapsOtherOAuthBadRequestToIntegrationFailure() {
        RuntimeException mapped = mapper.mapOAuthFailure(response(400, "{\"error\":\"invalid_request\"}"));

        assertThat(mapped)
                .isExactlyInstanceOf(FuelFinderIntegrationException.class)
                .hasMessageContaining("400");
    }

    @Test
    void mapsExhaustedOAuthTransientStatusToIntegrationFailure() {
        RuntimeException mapped = mapper.mapOAuthFailure(response(503, "{\"error\":\"unavailable\"}"));

        assertThat(mapped)
                .isExactlyInstanceOf(FuelFinderIntegrationException.class)
                .hasMessageContaining("503");
    }

    @Test
    void mapsTransportFailureToConnectivityFailure() {
        WebClientRequestException failure = new WebClientRequestException(
                new IOException("connection reset"),
                HttpMethod.GET,
                URI.create("https://example.test/pfs"),
                HttpHeaders.EMPTY
        );

        assertThat(mapper.mapApiFailure("PFS request", failure))
                .isInstanceOf(FuelFinderConnectivityException.class)
                .hasCause(failure);
    }

    private static WebClientResponseException response(int status, String body) {
        return WebClientResponseException.create(
                status,
                "test",
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }
}
