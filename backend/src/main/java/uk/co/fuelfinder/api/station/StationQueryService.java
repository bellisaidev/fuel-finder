package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistoryResponse;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StationQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_HISTORY_LIMIT = 100;
    private static final int MAX_HISTORY_LIMIT = 1000;

    private final CachedStationQueryService cachedStationQueryService;

    public List<NearbyStationResponse> findNearbyStations(
            double lat,
            double lon,
            double radiusMeters,
            String fuelType,
            Integer limit
    ) {
        return cachedStationQueryService.findNearbyStations(normalizeQuery(lat, lon, radiusMeters, fuelType, limit));
    }

    public List<NearbyStationResponse> findCheapestNearbyStations(
            double lat,
            double lon,
            double radiusMeters,
            String fuelType,
            Integer limit
    ) {
        return cachedStationQueryService.findCheapestNearbyStations(normalizeQuery(lat, lon, radiusMeters, fuelType, limit));
    }

    public StationDetailsResponse getStationDetails(UUID stationId) {
        return cachedStationQueryService.getStationDetails(stationId);
    }

    public StationPriceHistoryResponse getStationPriceHistory(
            UUID stationId,
            String fuelType,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {
        validateHistoryBounds(from, to);

        return cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(
                stationId,
                validateAndNormalizeFuelType(fuelType),
                from,
                to,
                normalizeHistoryLimit(limit)
        ));
    }

    private String validateAndNormalizeFuelType(String fuelType) {
        if (fuelType == null || fuelType.isBlank()) {
            throw new IllegalArgumentException("fuelType must not be blank");
        }

        return fuelType.trim().toUpperCase();
    }

    private int normalizeLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;

        if (resolved <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }

        if (resolved > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be less than or equal to 100");
        }

        return resolved;
    }

    private int normalizeHistoryLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_HISTORY_LIMIT : limit;

        if (resolved <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }

        if (resolved > MAX_HISTORY_LIMIT) {
            throw new IllegalArgumentException("limit must be less than or equal to 1000");
        }

        return resolved;
    }

    private void validateHistoryBounds(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must be less than or equal to to");
        }
    }

    private NormalizedStationQuery normalizeQuery(
            double lat,
            double lon,
            double radiusMeters,
            String fuelType,
            Integer limit
    ) {
        return new NormalizedStationQuery(
                lat,
                lon,
                radiusMeters,
                validateAndNormalizeFuelType(fuelType),
                normalizeLimit(limit)
        );
    }

}
