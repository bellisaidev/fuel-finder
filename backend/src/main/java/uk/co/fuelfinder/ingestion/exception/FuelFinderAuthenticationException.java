package uk.co.fuelfinder.ingestion.exception;

public class FuelFinderAuthenticationException extends FuelFinderIntegrationException {

    public FuelFinderAuthenticationException(String message) {
        super(message);
    }

    public FuelFinderAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}