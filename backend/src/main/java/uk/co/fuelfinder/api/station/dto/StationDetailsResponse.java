package uk.co.fuelfinder.api.station.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Detailed station response with address, coordinates, and latest prices.")
public record StationDetailsResponse(
        @Schema(description = "Internal station identifier.", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID stationId,
        @Schema(description = "Retailer-specific site identifier.", example = "SITE-1")
        String siteId,
        @Schema(description = "Station brand.", example = "Shell")
        String brand,
        @Schema(description = "Street address.", example = "221B Baker Street")
        String address,
        @Schema(description = "City or locality.", example = "London")
        String city,
        @Schema(description = "County or region.", example = "Greater London")
        String county,
        @Schema(description = "Country.", example = "UK")
        String country,
        @Schema(description = "Postal code.", example = "NW1 6XE")
        String postcode,
        @Schema(description = "Latitude in decimal degrees.", example = "51.5237", nullable = true)
        Double latitude,
        @Schema(description = "Longitude in decimal degrees.", example = "-0.1585", nullable = true)
        Double longitude,
        @ArraySchema(schema = @Schema(implementation = LatestStationPriceResponse.class))
        List<LatestStationPriceResponse> latestPrices
) {
}
