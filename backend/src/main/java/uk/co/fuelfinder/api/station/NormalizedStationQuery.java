package uk.co.fuelfinder.api.station;

public record NormalizedStationQuery(
        double lat,
        double lon,
        long radiusMeters,
        String fuelType,
        int limit
) {
}
