package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.persistence.repository.StationQueryRepository;
import uk.co.fuelfinder.persistence.repository.projection.NearbyStationProjection;

import java.util.List;

import static uk.co.fuelfinder.config.StationQueryCacheConfig.CHEAPEST_NEARBY_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.NEARBY_STATIONS_CACHE;

@Service
@RequiredArgsConstructor
public class CachedStationQueryService {

    private final StationQueryRepository stationQueryRepository;

    @Cacheable(cacheNames = NEARBY_STATIONS_CACHE)
    public List<NearbyStationResponse> findNearbyStations(NormalizedStationQuery query) {
        return stationQueryRepository.findNearbyStations(
                        query.lat(),
                        query.lon(),
                        query.radiusMeters(),
                        query.fuelType(),
                        query.limit()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(cacheNames = CHEAPEST_NEARBY_STATIONS_CACHE)
    public List<NearbyStationResponse> findCheapestNearbyStations(NormalizedStationQuery query) {
        return stationQueryRepository.findCheapestNearbyStations(
                        query.lat(),
                        query.lon(),
                        query.radiusMeters(),
                        query.fuelType(),
                        query.limit()
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
}
