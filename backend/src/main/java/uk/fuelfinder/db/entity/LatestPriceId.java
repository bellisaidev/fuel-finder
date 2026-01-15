package uk.fuelfinder.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LatestPriceId implements Serializable {
    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType;

    // getters/setters, equals/hashCode
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LatestPriceId that)) return false;
        return Objects.equals(stationId, that.stationId) && Objects.equals(fuelType, that.fuelType);
    }
    @Override public int hashCode() { return Objects.hash(stationId, fuelType); }
}
