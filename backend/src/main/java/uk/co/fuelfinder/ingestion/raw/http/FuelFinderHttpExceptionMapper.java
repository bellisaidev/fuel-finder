package uk.co.fuelfinder.ingestion.raw.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;

import java.util.Set;

@Component
public class FuelFinderHttpExceptionMapper {

    private static final Set<String> OAUTH_CREDENTIAL_ERRORS = Set.of(
            "invalid_client",
            "unauthorized_client"
    );

    private final FuelFinderHttpResilience resilience;
    private final ObjectMapper objectMapper;

    public FuelFinderHttpExceptionMapper(
            FuelFinderHttpResilience resilience,
            ObjectMapper objectMapper
    ) {
        this.resilience = resilience;
        this.objectMapper = objectMapper;
    }

    public RuntimeException mapApiFailure(String operation, RuntimeException failure) {
        if (failure instanceof FuelFinderIntegrationException integrationException) {
            return integrationException;
        }
        if (resilience.isRetryableTransportFailure(failure)) {
            return connectivityFailure(operation, failure);
        }
        if (failure instanceof WebClientResponseException responseException) {
            if (responseException.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                    || responseException.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                return new FuelFinderAuthenticationException(
                        operation + " failed authentication: status=" + responseException.getStatusCode()
                                + ", responseBody=" + responseException.getResponseBodyAsString(),
                        responseException
                );
            }
            return httpFailure(operation, responseException);
        }
        return new FuelFinderIntegrationException(operation + " failed unexpectedly", failure);
    }

    public RuntimeException mapOAuthFailure(RuntimeException failure) {
        String operation = "Fuel Finder token request";
        if (failure instanceof FuelFinderIntegrationException integrationException) {
            return integrationException;
        }
        if (resilience.isRetryableTransportFailure(failure)) {
            return connectivityFailure(operation, failure);
        }
        if (failure instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == HttpStatus.UNAUTHORIZED.value()
                    || status == HttpStatus.FORBIDDEN.value()
                    || isOAuthCredentialError(responseException)) {
                return new FuelFinderAuthenticationException(
                        operation + " failed authentication: status=" + responseException.getStatusCode()
                                + ", responseBody=" + responseException.getResponseBodyAsString(),
                        responseException
                );
            }
            return httpFailure(operation, responseException);
        }
        return new FuelFinderIntegrationException("Unexpected error during Fuel Finder token acquisition", failure);
    }

    private boolean isOAuthCredentialError(WebClientResponseException responseException) {
        if (!responseException.getStatusCode().is4xxClientError()) {
            return false;
        }
        try {
            JsonNode body = objectMapper.readTree(responseException.getResponseBodyAsString());
            return body != null && OAUTH_CREDENTIAL_ERRORS.contains(body.path("error").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static FuelFinderConnectivityException connectivityFailure(
            String operation,
            RuntimeException failure
    ) {
        return new FuelFinderConnectivityException(
                operation + " failed due to a transient connectivity issue",
                failure
        );
    }

    private static FuelFinderIntegrationException httpFailure(
            String operation,
            WebClientResponseException failure
    ) {
        return new FuelFinderIntegrationException(
                operation + " failed: status=" + failure.getStatusCode()
                        + ", responseBody=" + failure.getResponseBodyAsString(),
                failure
        );
    }
}
