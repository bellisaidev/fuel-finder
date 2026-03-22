package uk.co.fuelfinder.ingestion.exception;

public class FuelFinderInvalidResponseException extends FuelFinderIntegrationException {

    public FuelFinderInvalidResponseException(String message) {
        super(message);
    }

    public FuelFinderInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}