package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.api.station.dto.StationMapMarkerResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistoryResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryResponse;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StationQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_BOUNDS_LIMIT = 250;
    private static final int MAX_BOUNDS_LIMIT = 500;
    private static final int DEFAULT_HISTORY_LIMIT = 100;
    private static final int MAX_HISTORY_LIMIT = 1000;
    private static final int DEFAULT_HISTORY_SUMMARY_LIMIT = 30;
    private static final int MAX_HISTORY_SUMMARY_LIMIT = 365;

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

    public List<StationMapMarkerResponse> findStationsInBounds(
            String bbox,
            String fuelType,
            Integer limit
    ) {
        return cachedStationQueryService.findStationsInBounds(normalizeBoundsQuery(bbox, fuelType, limit));
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

    public StationPriceHistorySummaryResponse getStationPriceHistorySummary(
            UUID stationId,
            String fuelType,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {
        validateHistoryBounds(from, to);

        return cachedStationQueryService.getStationPriceHistorySummary(new NormalizedStationPriceHistorySummaryQuery(
                stationId,
                validateAndNormalizeFuelType(fuelType),
                from,
                to,
                normalizeHistorySummaryLimit(limit)
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

    private int normalizeBoundsLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_BOUNDS_LIMIT : limit;

        if (resolved <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }

        if (resolved > MAX_BOUNDS_LIMIT) {
            throw new IllegalArgumentException("limit must be less than or equal to 500");
        }

        return resolved;
    }

    private int normalizeHistorySummaryLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_HISTORY_SUMMARY_LIMIT : limit;

        if (resolved <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }

        if (resolved > MAX_HISTORY_SUMMARY_LIMIT) {
            throw new IllegalArgumentException("limit must be less than or equal to 365");
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

    private NormalizedStationBoundsQuery normalizeBoundsQuery(
            String bbox,
            String fuelType,
            Integer limit
    ) {
        double[] bounds = parseBbox(bbox);
        double west = bounds[0];
        double south = bounds[1];
        double east = bounds[2];
        double north = bounds[3];

        validateLongitude(west, "west");
        validateLatitude(south, "south");
        validateLongitude(east, "east");
        validateLatitude(north, "north");

        if (west >= east) {
            throw new IllegalArgumentException("bbox west must be less than east");
        }

        if (south >= north) {
            throw new IllegalArgumentException("bbox south must be less than north");
        }

        return new NormalizedStationBoundsQuery(
                west,
                south,
                east,
                north,
                validateAndNormalizeFuelType(fuelType),
                normalizeBoundsLimit(limit)
        );
    }

    private double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            throw new IllegalArgumentException("bbox must not be blank");
        }

        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("bbox must contain west,south,east,north");
        }

        double[] bounds = new double[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                bounds[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("bbox must contain numeric values");
            }

            if (!Double.isFinite(bounds[i])) {
                throw new IllegalArgumentException("bbox must contain finite values");
            }
        }

        return bounds;
    }

    private void validateLatitude(double value, String name) {
        if (value < -90.0 || value > 90.0) {
            throw new IllegalArgumentException("bbox " + name + " must be between -90 and 90");
        }
    }

    private void validateLongitude(double value, String name) {
        if (value < -180.0 || value > 180.0) {
            throw new IllegalArgumentException("bbox " + name + " must be between -180 and 180");
        }
    }

}
