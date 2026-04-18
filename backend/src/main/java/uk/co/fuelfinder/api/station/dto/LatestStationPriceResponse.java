package uk.co.fuelfinder.api.station.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Latest available price for a station fuel type.")
public record LatestStationPriceResponse(
        @Schema(description = "Fuel type code.", example = "E5")
        String fuelType,
        @Schema(description = "Price in pence.", example = "145")
        int pricePence,
        @Schema(description = "When the price observation was recorded.", example = "2026-04-18T10:15:30Z")
        OffsetDateTime observedAt,
        @Schema(description = "When the source reported the price was last updated, if available.", example = "2026-04-18T10:10:00Z")
        OffsetDateTime reportedUpdatedAt
) {
}
