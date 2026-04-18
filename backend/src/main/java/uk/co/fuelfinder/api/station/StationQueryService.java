package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StationQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

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
