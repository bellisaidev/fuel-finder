package uk.co.fuelfinder.ingestion.raw.auth;

public record TokenResponse(
        boolean success,
        TokenData data,
        String message
) {
    public record TokenData(
            String access_token,
            String token_type,
            long expires_in,
            String refresh_token
    ) {
    }
}