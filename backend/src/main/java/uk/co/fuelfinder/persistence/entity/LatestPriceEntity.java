package uk.co.fuelfinder.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "latest_price")
public class LatestPriceEntity {

    @EmbeddedId
    private LatestPriceId id;

    @MapsId("stationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private StationEntity station;

    @Column(name = "price_pence", nullable = false)
    private int pricePence;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "reported_updated_at")
    private OffsetDateTime reportedUpdatedAt;
}
