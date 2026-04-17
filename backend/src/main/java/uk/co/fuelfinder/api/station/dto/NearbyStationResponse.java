package uk.co.fuelfinder.api.station.dto;

import java.util.UUID;

public record NearbyStationResponse(
        UUID stationId,
        String siteId,
        String brand,
        String address,
        String city,
        String county,
        String country,
        String postcode,
        String fuelType,
        int pricePence,
        double distanceMeters
) {
}
