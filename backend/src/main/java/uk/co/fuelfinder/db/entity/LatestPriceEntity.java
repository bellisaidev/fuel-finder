package uk.co.fuelfinder.db.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

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
    private String currency = "GBP";

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "reported_updated_at")
    private OffsetDateTime reportedUpdatedAt;

}
