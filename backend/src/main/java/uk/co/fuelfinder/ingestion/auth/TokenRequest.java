package uk.co.fuelfinder.ingestion.auth;

public record TokenRequest(
        String client_id,
        String client_secret
) {
}