package uk.co.fuelfinder.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.co.fuelfinder.ingestion.raw.FeedType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "raw_feed_fetch")
public class RawFeedFetchEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private RetailerEntity retailer;

    @Column(name = "feed_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private FeedType feedType;

    @Column(name = "endpoint_path", nullable = false)
    private String endpointPath;

    @Column(name = "batch_number", nullable = false)
    private int batchNumber;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", columnDefinition = "jsonb", nullable = false)
    private JsonNode rawJson;
}