package uk.co.fuelfinder.api.station.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Historical price observations for a station and fuel type.")
public record StationPriceHistoryResponse(
        @Schema(description = "Internal station identifier.", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID stationId,
        @Schema(description = "Fuel type code.", example = "E5")
        String fuelType,
        @Schema(description = "Inclusive lower observedAt bound applied to the query, if provided.", example = "2026-04-18T00:00:00Z", nullable = true)
        OffsetDateTime from,
        @Schema(description = "Inclusive upper observedAt bound applied to the query, if provided.", example = "2026-04-19T00:00:00Z", nullable = true)
        OffsetDateTime to,
        @ArraySchema(schema = @Schema(implementation = StationPriceObservationResponse.class))
        List<StationPriceObservationResponse> observations
) {
}
