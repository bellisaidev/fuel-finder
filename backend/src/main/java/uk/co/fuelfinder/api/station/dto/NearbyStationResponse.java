package uk.co.fuelfinder.api.station.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Nearby station search result.")
public record NearbyStationResponse(
        @Schema(description = "Internal unique station identifier.", example = "00000000-0000-0000-0000-000000000001")
        UUID stationId,
        @Schema(description = "External site identifier from the data source.", example = "example-site-id")
        String siteId,
        @Schema(description = "Station brand or retailer name.", example = "Example Fuel")
        String brand,
        @Schema(description = "Street address.", example = "123 Example Road")
        String address,
        @Schema(description = "City or locality.", example = "Exampletown")
        String city,
        @Schema(description = "County or administrative area.", example = "Example County")
        String county,
        @Schema(description = "Country.", example = "United Kingdom")
        String country,
        @Schema(description = "Postal code.", example = "EX4 MPL")
        String postcode,
        @Schema(description = "Fuel type code for the returned price.", example = "E5")
        String fuelType,
        @Schema(description = "Fuel price in pence.", example = "139")
        int pricePence,
        @Schema(description = "Distance from the requested point in meters.", example = "824.5")
        double distanceMeters
) {
}
