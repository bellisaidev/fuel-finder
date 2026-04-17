package uk.co.fuelfinder.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API error response.")
public record ApiErrorResponse(
        @Schema(description = "Timestamp of the error in ISO-8601 format.", example = "2026-04-17T18:35:42.123456+02:00")
        String timestamp,
        @Schema(description = "HTTP status code.", example = "400")
        int status,
        @Schema(description = "HTTP reason phrase.", example = "Bad Request")
        String error,
        @Schema(description = "Application error message.", example = "radiusMeters must be greater than 0")
        String message
) {
}
