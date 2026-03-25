package uk.co.fuelfinder.ingestion.raw.auth;

public record TokenRequest(
        String client_id,
        String client_secret
) {
}