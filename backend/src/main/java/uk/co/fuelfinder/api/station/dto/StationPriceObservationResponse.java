package uk.co.fuelfinder.api.station.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Single historical price observation for a station fuel type.")
public record StationPriceObservationResponse(
        @Schema(description = "Price in pence.", example = "145")
        int pricePence,
        @Schema(description = "When the price observation was recorded.", example = "2026-04-18T10:15:30Z")
        OffsetDateTime observedAt
) {
}
