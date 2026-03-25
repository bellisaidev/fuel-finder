package uk.co.fuelfinder.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.co.fuelfinder.ingestion.raw.FeedType;
import uk.co.fuelfinder.persistence.converter.JsonNodeConverter;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "feed_type", nullable = false, length = 50)
    private FeedType feedType;

    @Column(name = "endpoint_path", nullable = false, length = 255)
    private String endpointPath;

    @Column(name = "batch_number", nullable = false)
    private int batchNumber;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode rawJson;
}