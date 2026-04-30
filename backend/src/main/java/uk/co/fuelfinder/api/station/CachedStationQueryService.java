package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uk.co.fuelfinder.api.StationNotFoundException;
import uk.co.fuelfinder.api.station.dto.LatestStationPriceResponse;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.api.station.dto.StationMapMarkerResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistoryResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryBucketResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceObservationResponse;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;
import uk.co.fuelfinder.persistence.entity.LatestPriceEntity;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.LatestPriceRepository;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;
import uk.co.fuelfinder.persistence.repository.StationQueryRepository;
import uk.co.fuelfinder.persistence.repository.projection.NearbyStationProjection;
import uk.co.fuelfinder.persistence.repository.projection.StationMapMarkerProjection;
import uk.co.fuelfinder.persistence.repository.projection.StationPriceHistorySummaryBucketProjection;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.time.ZoneOffset;

import static uk.co.fuelfinder.config.StationQueryCacheConfig.CHEAPEST_NEARBY_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.IN_BOUNDS_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.NEARBY_STATIONS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_DETAILS_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_PRICE_HISTORY_CACHE;
import static uk.co.fuelfinder.config.StationQueryCacheConfig.STATION_PRICE_HISTORY_SUMMARY_CACHE;

@Service
@RequiredArgsConstructor
public class CachedStationQueryService {

    private final StationQueryRepository stationQueryRepository;
    private final StationRepository stationRepository;
    private final LatestPriceRepository latestPriceRepository;
    private final PriceObservationRepository priceObservationRepository;

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

    @Cacheable(cacheNames = IN_BOUNDS_STATIONS_CACHE)
    public List<StationMapMarkerResponse> findStationsInBounds(NormalizedStationBoundsQuery query) {
        return stationQueryRepository.findStationsInBounds(
                        query.west(),
                        query.south(),
                        query.east(),
                        query.north(),
                        query.fuelType(),
                        query.limit()
                )
                .stream()
                .map(this::toMapMarkerResponse)
                .toList();
    }

    @Cacheable(cacheNames = STATION_DETAILS_CACHE)
    public StationDetailsResponse getStationDetails(UUID stationId) {
        StationEntity station = stationRepository.findByIdAndActiveTrue(stationId)
                .orElseThrow(() -> new StationNotFoundException(stationId));

        List<LatestStationPriceResponse> latestPrices = latestPriceRepository.findByStationId(stationId).stream()
                .sorted(Comparator.comparing(latestPrice -> latestPrice.getId().getFuelType()))
                .map(this::toLatestPriceResponse)
                .toList();

        Double latitude = station.getLocation() == null ? null : station.getLocation().getY();
        Double longitude = station.getLocation() == null ? null : station.getLocation().getX();

        return new StationDetailsResponse(
                station.getId(),
                station.getSiteId(),
                station.getBrand(),
                station.getAddress(),
                station.getCity(),
                station.getCounty(),
                station.getCountry(),
                station.getPostcode(),
                latitude,
                longitude,
                latestPrices
        );
    }

    @Cacheable(cacheNames = STATION_PRICE_HISTORY_CACHE)
    public StationPriceHistoryResponse getStationPriceHistory(NormalizedStationPriceHistoryQuery query) {
        stationRepository.findById(query.stationId())
                .orElseThrow(() -> new StationNotFoundException(query.stationId()));

        PageRequest pageRequest = PageRequest.of(0, query.limit());
        List<PriceObservationEntity> history;

        if (query.from() != null && query.to() != null) {
            history = priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
                    query.stationId(),
                    query.fuelType(),
                    query.from(),
                    query.to(),
                    pageRequest
            );
        } else if (query.from() != null) {
            history = priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
                    query.stationId(),
                    query.fuelType(),
                    query.from(),
                    pageRequest
            );
        } else if (query.to() != null) {
            history = priceObservationRepository.findByStationIdAndFuelTypeAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
                    query.stationId(),
                    query.fuelType(),
                    query.to(),
                    pageRequest
            );
        } else {
            history = priceObservationRepository.findByStationIdAndFuelTypeOrderByObservedAtDescIdDesc(
                    query.stationId(),
                    query.fuelType(),
                    pageRequest
            );
        }

        List<StationPriceObservationResponse> observations = history.stream()
                .map(this::toStationPriceObservationResponse)
                .toList();

        return new StationPriceHistoryResponse(
                query.stationId(),
                query.fuelType(),
                query.from(),
                query.to(),
                observations
        );
    }

    @Cacheable(cacheNames = STATION_PRICE_HISTORY_SUMMARY_CACHE)
    public StationPriceHistorySummaryResponse getStationPriceHistorySummary(NormalizedStationPriceHistorySummaryQuery query) {
        stationRepository.findById(query.stationId())
                .orElseThrow(() -> new StationNotFoundException(query.stationId()));

        List<StationPriceHistorySummaryBucketResponse> summaries = priceObservationRepository
                .findDailySummaryByStationIdAndFuelType(
                        query.stationId(),
                        query.fuelType(),
                        query.from(),
                        query.to(),
                        query.limit()
                )
                .stream()
                .map(this::toStationPriceHistorySummaryBucketResponse)
                .toList();

        return new StationPriceHistorySummaryResponse(
                query.stationId(),
                query.fuelType(),
                query.from(),
                query.to(),
                "DAILY",
                "UTC",
                summaries
        );
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

    private StationMapMarkerResponse toMapMarkerResponse(StationMapMarkerProjection projection) {
        return new StationMapMarkerResponse(
                projection.getStationId(),
                projection.getSiteId(),
                projection.getBrand(),
                projection.getPostcode(),
                projection.getLatitude() == null ? 0d : projection.getLatitude(),
                projection.getLongitude() == null ? 0d : projection.getLongitude(),
                projection.getFuelType(),
                projection.getPricePence()
        );
    }

    private LatestStationPriceResponse toLatestPriceResponse(LatestPriceEntity latestPrice) {
        return new LatestStationPriceResponse(
                latestPrice.getId().getFuelType(),
                latestPrice.getPricePence(),
                latestPrice.getObservedAt(),
                latestPrice.getReportedUpdatedAt()
        );
    }

    private StationPriceObservationResponse toStationPriceObservationResponse(PriceObservationEntity priceObservation) {
        return new StationPriceObservationResponse(
                priceObservation.getPricePence(),
                priceObservation.getObservedAt()
        );
    }

    private StationPriceHistorySummaryBucketResponse toStationPriceHistorySummaryBucketResponse(
            StationPriceHistorySummaryBucketProjection projection
    ) {
        return new StationPriceHistorySummaryBucketResponse(
                projection.getBucketStart().atOffset(ZoneOffset.UTC),
                projection.getBucketEnd().atOffset(ZoneOffset.UTC),
                projection.getFirstPricePence(),
                projection.getHighestPricePence(),
                projection.getLowestPricePence(),
                projection.getLastPricePence(),
                projection.getObservationCount()
        );
    }
}
