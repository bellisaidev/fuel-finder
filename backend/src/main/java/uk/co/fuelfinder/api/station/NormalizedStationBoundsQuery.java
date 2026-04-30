package uk.co.fuelfinder.api.station;

public record NormalizedStationBoundsQuery(
        double west,
        double south,
        double east,
        double north,
        String fuelType,
        int limit
) {
}
