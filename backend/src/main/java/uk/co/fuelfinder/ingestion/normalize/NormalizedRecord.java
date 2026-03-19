package uk.co.fuelfinder.ingestion.normalize;

import java.util.Objects;

public record NormalizedRecord(
        String siteId,
        String brand,
        String address,
        String postcode,
        double lat,
        double lon,
        String fuelType,
        int pricePence
) {
    public NormalizedRecord {
        Objects.requireNonNull(siteId, "siteId");
        Objects.requireNonNull(fuelType, "fuelType");
        if (pricePence <= 0) {
            throw new IllegalArgumentException("pricePence must be > 0, got " + pricePence);
        }
    }
}
