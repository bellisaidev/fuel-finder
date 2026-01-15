package uk.fuelfinder.db.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.fuelfinder.db.converters.JsonNodeConverter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "raw_feed_fetch")
public class RawFeedFetchEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "retailer_id", nullable = false)
    private RetailerEntity retailer;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "etag")
    private String etag;

    @Column(name = "last_modified")
    private String lastModified;

    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;

    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "parse_errors", columnDefinition = "jsonb")
    private JsonNode parseErrors;

    @Column(name = "station_count")
    private Integer stationCount;

}
