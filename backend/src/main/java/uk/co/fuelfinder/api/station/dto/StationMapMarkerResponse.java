package uk.co.fuelfinder.api.station.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Station marker visible in a map viewport.")
public record StationMapMarkerResponse(
        @Schema(description = "Internal unique station identifier.", example = "00000000-0000-0000-0000-000000000001")
        UUID stationId,
        @Schema(description = "External site identifier from the data source.", example = "example-site-id")
        String siteId,
        @Schema(description = "Station brand or retailer name.", example = "Example Fuel")
        String brand,
        @Schema(description = "Postal code.", example = "EX4 MPL")
        String postcode,
        @Schema(description = "Latitude in decimal degrees.", example = "51.5074")
        double latitude,
        @Schema(description = "Longitude in decimal degrees.", example = "-0.1278")
        double longitude,
        @Schema(description = "Fuel type code for the returned price.", example = "E5")
        String fuelType,
        @Schema(description = "Fuel price in pence.", example = "139")
        int pricePence
) {
}
