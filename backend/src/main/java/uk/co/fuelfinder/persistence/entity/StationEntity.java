package uk.co.fuelfinder.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "station",
        uniqueConstraints = @UniqueConstraint(name = "uq_station_retailer_site", columnNames = {"retailer_id", "site_id"})
)
public class StationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private RetailerEntity retailer;

    @Column(name = "site_id", nullable = false)
    private String siteId;

    @Column(name = "brand")
    private String brand;

    @Column(name = "address")
    private String address;

    @Column(name = "postcode")
    private String postcode;

    // PostGIS column is GEOGRAPHY(Point,4326)
    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

}