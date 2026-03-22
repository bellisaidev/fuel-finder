package uk.co.fuelfinder.ingestion.exception;

public class FuelFinderIntegrationException extends RuntimeException {

    public FuelFinderIntegrationException(String message) {
        super(message);
    }

    public FuelFinderIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}