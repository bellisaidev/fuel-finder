package uk.co.fuelfinder.ingestion.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FuelFinderExceptionsTest {

    @Test
    void authenticationExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");

        FuelFinderAuthenticationException exception =
                new FuelFinderAuthenticationException("auth failed", cause);

        assertThat(exception).hasMessage("auth failed").hasCause(cause);
    }

    @Test
    void connectivityExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");

        FuelFinderConnectivityException exception =
                new FuelFinderConnectivityException("connectivity failed", cause);

        assertThat(exception).hasMessage("connectivity failed").hasCause(cause);
    }

    @Test
    void integrationExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");

        FuelFinderIntegrationException exception =
                new FuelFinderIntegrationException("integration failed", cause);

        assertThat(exception).hasMessage("integration failed").hasCause(cause);
    }

    @Test
    void invalidResponseExceptionKeepsMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");

        FuelFinderInvalidResponseException exception =
                new FuelFinderInvalidResponseException("invalid response", cause);

        assertThat(exception).hasMessage("invalid response").hasCause(cause);
    }
}
