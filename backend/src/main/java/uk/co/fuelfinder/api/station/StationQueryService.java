package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.persistence.repository.StationQueryRepository;
import uk.co.fuelfinder.persistence.repository.projection.NearbyStationProjection;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StationQueryService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final StationQueryRepository stationQueryRepository;

    public List<NearbyStationResponse> findNearbyStations(
            double lat,
            double lon,
            double radiusMeters,
            String fuelType,
            Integer limit
    ) {
        validateLat(lat);
        validateLon(lon);
        validateRadius(radiusMeters);

        String normalizedFuelType = validateAndNormalizeFuelType(fuelType);
        int normalizedLimit = normalizeLimit(limit);

        return stationQueryRepository.findNearbyStations(
                        lat,
                        lon,
                        radiusMeters,
                        normalizedFuelType,
                        normalizedLimit
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private NearbyStationResponse toResponse(NearbyStationProjection projection) {
        return new NearbyStationResponse(
                projection.getStationId(),
                projection.getSiteId(),
                projection.getBrand(),
                projection.getAddress(),
                projection.getCity(),
                projection.getCounty(),
                projection.getCountry(),
                projection.getPostcode(),
                projection.getFuelType(),
                projection.getPricePence(),
                projection.getDistanceMeters() == null ? 0d : projection.getDistanceMeters()
        );
    }

    private void validateLat(double lat) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be between -90 and 90");
        }
    }

    private void validateLon(double lon) {
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("lon must be between -180 and 180");
        }
    }

    private void validateRadius(double radiusMeters) {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be greater than 0");
        }
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
}
