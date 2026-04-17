package uk.co.fuelfinder.api.station;

public record NormalizedStationQuery(
        double lat,
        double lon,
        double radiusMeters,
        String fuelType,
        int limit
) {
}
