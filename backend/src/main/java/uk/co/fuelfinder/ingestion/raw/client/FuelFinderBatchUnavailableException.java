package uk.co.fuelfinder.ingestion.raw.client;

class FuelFinderBatchUnavailableException extends RuntimeException {

    FuelFinderBatchUnavailableException(String message) {
        super(message);
    }
}
