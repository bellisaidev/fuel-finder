package uk.co.fuelfinder.ingestion.exception;

public class FuelFinderConnectivityException extends FuelFinderIntegrationException {

    public FuelFinderConnectivityException(String message) {
        super(message);
    }

    public FuelFinderConnectivityException(String message, Throwable cause) {
        super(message, cause);
    }
}