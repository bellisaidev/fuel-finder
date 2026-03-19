package uk.co.fuelfinder.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.fuelfinder.common.HashingUtils;
import uk.co.fuelfinder.db.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.db.entity.RetailerEntity;
import uk.co.fuelfinder.db.entity.StationEntity;
import uk.co.fuelfinder.db.repo.RawFeedFetchRepository;
import uk.co.fuelfinder.db.repo.StationRepository;
import uk.co.fuelfinder.ingestion.fetch.RetailerFeedClient;
import uk.co.fuelfinder.ingestion.fetch.RetailerFeedClient.FetchResult;
import uk.co.fuelfinder.ingestion.normalize.NormalizedRecord;
import uk.co.fuelfinder.ingestion.parse.RetailerFeedMapper;
import uk.co.fuelfinder.ingestion.write.LatestPriceWriteRepository;
import uk.co.fuelfinder.ingestion.write.PriceObservationWriteRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RetailerIngestionService {

    private final RetailerFeedClient client;
    private final RetailerFeedMapper mapper;
    private final ObjectMapper objectMapper;

    private final RawFeedFetchRepository rawFeedFetchRepository;
    private final StationRepository stationRepository;

    private final PriceObservationWriteRepository priceObservationWriteRepository;
    private final LatestPriceWriteRepository latestPriceWriteRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public RetailerIngestionService(RetailerFeedClient client,
                                    RetailerFeedMapper mapper,
                                    ObjectMapper objectMapper,
                                    RawFeedFetchRepository rawFeedFetchRepository,
                                    StationRepository stationRepository,
                                    PriceObservationWriteRepository priceObservationWriteRepository,
                                    LatestPriceWriteRepository latestPriceWriteRepository) {
        this.client = client;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.rawFeedFetchRepository = rawFeedFetchRepository;
        this.stationRepository = stationRepository;
        this.priceObservationWriteRepository = priceObservationWriteRepository;
        this.latestPriceWriteRepository = latestPriceWriteRepository;
    }

    @Transactional
    public IngestionSummary ingest(RetailerEntity retailer) {
        OffsetDateTime fetchedAt = OffsetDateTime.now(ZoneOffset.UTC);

        FetchResult fetch = client.fetch(retailer.getFeedUrl());
        String body = fetch.getBody(); // may be null

        // DB requires NOT NULL payload_hash + payload
        String payloadHash = HashingUtils.sha256(body == null ? "" : body);
        JsonNode payloadNode = parseJsonNodeOrEmpty(body);

        // Save audit row first, always
        RawFeedFetchEntity raw = RawFeedFetchEntity.builder()
                .id(UUID.randomUUID())
                .retailer(retailer)
                .fetchedAt(fetchedAt)
                .httpStatus(fetch.getHttpStatus())
                // client doesn't expose these yet -> keep null (but entity supports them)
                .etag(null)
                .lastModified(null)
                .payloadHash(payloadHash)
                .payload(payloadNode)
                .parseErrors(null)
                .stationCount(null)
                .build();

        rawFeedFetchRepository.save(raw);

        // Bail out early on HTTP failure / empty body (audit already written)
        if (fetch.getHttpStatus() != 200 || body == null || body.isBlank()) {
            raw.setParseErrors(objectMapper.createObjectNode()
                    .put("error", "HTTP_FAILED_OR_EMPTY_BODY")
                    .put("httpStatus", fetch.getHttpStatus())
                    .put("message", safe(fetch.getError())));
            rawFeedFetchRepository.save(raw); // 2nd save only when we add parseErrors

            log.warn("Ingestion aborted retailer={} httpStatus={} err={}",
                    retailer.getName(), fetch.getHttpStatus(), fetch.getError());

            return IngestionSummary.failed(retailer.getName(), fetchedAt, "HTTP_FAILED_OR_EMPTY_BODY");
        }

        // Parse + normalize
        final List<NormalizedRecord> records;
        final Integer stationCount;
        try {
            records = mapper.mapToNormalizedRecords(body);
            stationCount = extractStationCount(payloadNode); // deterministic audit metadata
        } catch (Exception e) {
            raw.setParseErrors(objectMapper.createObjectNode()
                    .put("error", "PARSE_FAILED")
                    .put("message", safe(e.getMessage())));
            rawFeedFetchRepository.save(raw); // 2nd save only when we add parseErrors

            log.error("Parse failed retailer={} rawFetchId={}", retailer.getName(), raw.getId(), e);
            return IngestionSummary.failed(retailer.getName(), fetchedAt, "PARSE_FAILED");
        }

        // Update audit metadata after successful parse (2nd save in success path)
        raw.setStationCount(stationCount);
        rawFeedFetchRepository.save(raw);

        // Use one observedAt timestamp for the whole batch (append-only model uses observed_at as ingestion time)
        OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.UTC);

        int stationUpserts = 0;
        int observationsInserted = 0;
        int latestUpserts = 0;

        for (NormalizedRecord r : records) {
            StationEntity station = upsertStation(retailer, r);
            stationUpserts++;

            UUID obsId = UUID.randomUUID();

            String sourceHash = HashingUtils.sha256(
                    retailer.getId() + "|" + r.siteId() + "|" + r.fuelType() + "|" + r.pricePence()
            );

            int inserted = priceObservationWriteRepository.insertIfNotExists(
                    obsId,
                    station.getId(),
                    r.fuelType(),
                    r.pricePence(),
                    observedAt.toInstant(),
                    sourceHash
            );

            if (inserted == 1) {
                observationsInserted++;

                latestPriceWriteRepository.upsert(
                        station.getId(),
                        r.fuelType(),
                        r.pricePence(),
                        observedAt.toInstant(),
                        obsId
                );
                latestUpserts++;
            }
        }

        return IngestionSummary.success(retailer.getName(), fetchedAt, stationUpserts, observationsInserted, latestUpserts);
    }

    private StationEntity upsertStation(RetailerEntity retailer, NormalizedRecord r) {
        StationEntity station = stationRepository
                .findByRetailer_IdAndSiteId(retailer.getId(), r.siteId())
                .orElseGet(() -> {
                    StationEntity s = new StationEntity();
                    s.setId(UUID.randomUUID());
                    s.setRetailer(retailer);
                    s.setSiteId(r.siteId());
                    return s;
                });

        station.setBrand(r.brand());
        station.setAddress(r.address());
        station.setPostcode(r.postcode());
        station.setLocation(toPoint(r.lat(), r.lon()));

        return stationRepository.save(station);
    }

    private Point toPoint(double lat, double lon) {
        // JTS uses (x=lon, y=lat)
        Point p = geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private JsonNode parseJsonNodeOrEmpty(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            // Payload is not valid JSON; still persist {} to satisfy NOT NULL and keep ingestion flowing.
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Deterministic for audit: if stations is missing/not array, we store 0.
     */
    private Integer extractStationCount(JsonNode payloadNode) {
        JsonNode stations = payloadNode.path("stations");
        return stations.isArray() ? stations.size() : 0;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public record IngestionSummary(
            String retailerName,
            OffsetDateTime fetchedAt,
            boolean success,
            String failureReason,
            int stationUpserts,
            int observationsInserted,
            int latestUpserts
    ) {
        public static IngestionSummary success(String retailerName, OffsetDateTime fetchedAt,
                                               int stationUpserts, int observationsInserted, int latestUpserts) {
            return new IngestionSummary(retailerName, fetchedAt, true, null,
                    stationUpserts, observationsInserted, latestUpserts);
        }

        public static IngestionSummary failed(String retailerName, OffsetDateTime fetchedAt, String reason) {
            return new IngestionSummary(retailerName, fetchedAt, false, reason,
                    0, 0, 0);
        }
    }
}
