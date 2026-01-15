package uk.fuelfinder.db.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retailer")
public class RetailerEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "feed_url", nullable = false)
    private String feedUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "fetch_interval_seconds", nullable = false)
    private int fetchIntervalSeconds = 3600;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

}
