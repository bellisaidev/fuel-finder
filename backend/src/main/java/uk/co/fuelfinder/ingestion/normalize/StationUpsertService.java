package uk.co.fuelfinder.ingestion.normalize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.StationRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StationUpsertService {

    private static final int WGS84_SRID = 4326;

    private final StationRepository stationRepository;

    @Transactional
    public int upsert(RetailerEntity retailer, NormalizedStation normalizedStation) {
        validateInput(retailer, normalizedStation);

        Optional<StationEntity> existingOpt = stationRepository.findByRetailerIdAndSiteId(
                retailer.getId(),
                normalizedStation.getSiteId()
        );

        if (existingOpt.isPresent()) {
            StationEntity existing = existingOpt.get();
            updateExisting(existing, normalizedStation);
            stationRepository.save(existing);

            log.debug("Updated station: retailer={}, siteId={}", retailer.getName(), normalizedStation.getSiteId());
            return 1;
        }

        StationEntity entity = buildNewStation(retailer, normalizedStation);
        stationRepository.save(entity);

        log.debug("Inserted station: retailer={}, siteId={}", retailer.getName(), normalizedStation.getSiteId());
        return 1;
    }

    private StationEntity buildNewStation(RetailerEntity retailer, NormalizedStation normalizedStation) {
        OffsetDateTime now = OffsetDateTime.now();

        return StationEntity.builder()
                .id(UUID.randomUUID())
                .retailer(retailer)
                .siteId(normalizedStation.getSiteId())
                .brand(normalizedStation.getBrand())
                .address(normalizedStation.getAddress())
                .postcode(normalizedStation.getPostcode())
                .location(toPoint(normalizedStation.getLongitude(), normalizedStation.getLatitude()))
                .active(normalizedStation.isActive())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void updateExisting(StationEntity entity, NormalizedStation normalizedStation) {
        entity.setBrand(normalizedStation.getBrand());
        entity.setAddress(normalizedStation.getAddress());
        entity.setPostcode(normalizedStation.getPostcode());
        entity.setLocation(toPoint(normalizedStation.getLongitude(), normalizedStation.getLatitude()));
        entity.setActive(normalizedStation.isActive());
        entity.setUpdatedAt(OffsetDateTime.now());
    }

    private Point toPoint(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return null;
        }

        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), WGS84_SRID);
        Point point = geometryFactory.createPoint(new org.locationtech.jts.geom.Coordinate(longitude, latitude));
        point.setSRID(WGS84_SRID);
        return point;
    }

    private void validateInput(RetailerEntity retailer, NormalizedStation normalizedStation) {
        if (retailer == null) {
            throw new IllegalArgumentException("Retailer cannot be null");
        }

        if (normalizedStation == null) {
            throw new IllegalArgumentException("NormalizedStation cannot be null");
        }

        if (normalizedStation.getSiteId() == null || normalizedStation.getSiteId().isBlank()) {
            throw new IllegalArgumentException("NormalizedStation siteId cannot be null or blank");
        }
    }
}