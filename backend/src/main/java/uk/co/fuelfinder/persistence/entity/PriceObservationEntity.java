package uk.co.fuelfinder.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name = "price_observation",
        indexes = {
                @Index(name = "idx_price_obs_station_fuel_observed", columnList = "station_id,fuel_type,observed_at"),
                @Index(name = "idx_price_obs_fuel_observed", columnList = "fuel_type,observed_at")
        }
)
public class PriceObservationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private StationEntity station;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType;

    @Column(name = "price_pence", nullable = false)
    private int pricePence;

    @Builder.Default
    @Column(name = "currency", nullable = false)
    private String currency = "GBP";

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "reported_updated_at")
    private OffsetDateTime reportedUpdatedAt;

    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_payload_id")
    private RawFeedFetchEntity rawPayload;

}
