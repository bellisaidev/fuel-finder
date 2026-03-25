package uk.co.fuelfinder.ingestion.raw.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.common.HashingUtils;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;
import uk.co.fuelfinder.ingestion.raw.FeedType;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.repository.RawFeedFetchRepository;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RawFeedStorageService {

    private final RawFeedFetchRepository rawFeedFetchRepository;
    private final ObjectMapper objectMapper;

    public RawFeedFetchEntity store(
            RetailerEntity retailer,
            FeedType feedType,
            String endpointPath,
            int batchNumber,
            Object payload,
            int recordCount
    ) {
        validateInput(retailer, feedType, endpointPath, batchNumber, payload, recordCount);

        try {
            JsonNode rawJson = objectMapper.valueToTree(payload);
            String canonicalPayload = objectMapper.writeValueAsString(rawJson);
            String sourceHash = HashingUtils.sha256(canonicalPayload);

            RawFeedFetchEntity entity = RawFeedFetchEntity.builder()
                    .id(UUID.randomUUID())
                    .retailer(retailer)
                    .feedType(feedType)
                    .endpointPath(endpointPath)
                    .batchNumber(batchNumber)
                    .fetchedAt(OffsetDateTime.now())
                    .recordCount(recordCount)
                    .sourceHash(sourceHash)
                    .rawJson(rawJson)
                    .build();

            RawFeedFetchEntity savedEntity = rawFeedFetchRepository.save(entity);

            log.info(
                    "Stored raw feed successfully: retailer={}, feedType={}, endpointPath={}, batchNumber={}, recordCount={}, sourceHashPrefix={}, rawFeedFetchId={}",
                    retailer.getName(),
                    feedType,
                    endpointPath,
                    batchNumber,
                    recordCount,
                    sourceHash.substring(0, Math.min(8, sourceHash.length())),
                    savedEntity.getId()
            );

            return savedEntity;

        } catch (JsonProcessingException e) {
            throw new FuelFinderIntegrationException("Failed to serialize raw payload for storage", e);
        }
    }

    private void validateInput(
            RetailerEntity retailer,
            FeedType feedType,
            String endpointPath,
            int batchNumber,
            Object payload,
            int recordCount
    ) {
        Objects.requireNonNull(retailer, "Retailer cannot be null when storing raw feed");
        Objects.requireNonNull(feedType, "Feed type cannot be null when storing raw feed");
        Objects.requireNonNull(payload, "Raw payload cannot be null when storing raw feed");

        if (endpointPath == null || endpointPath.isBlank()) {
            throw new FuelFinderInvalidResponseException("Endpoint path cannot be null or blank when storing raw feed");
        }

        if (batchNumber < 1) {
            throw new FuelFinderInvalidResponseException("Batch number must be greater than zero");
        }

        if (recordCount < 0) {
            throw new FuelFinderInvalidResponseException("Record count cannot be negative");
        }
    }
}