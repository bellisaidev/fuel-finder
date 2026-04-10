package uk.co.fuelfinder.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class LatestPriceId implements Serializable {

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType;
}