package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PriceObservationRepository extends JpaRepository<PriceObservationEntity, UUID> {

    boolean existsBySourceHash(String sourceHash);

    List<PriceObservationEntity> findByStationIdAndFuelTypeOrderByObservedAtDescIdDesc(
            UUID stationId,
            String fuelType,
            Pageable pageable
    );

    List<PriceObservationEntity> findByStationIdAndFuelTypeAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
            UUID stationId,
            String fuelType,
            OffsetDateTime from,
            Pageable pageable
    );

    List<PriceObservationEntity> findByStationIdAndFuelTypeAndObservedAtLessThanEqualOrderByObservedAtDescIdDesc(
            UUID stationId,
            String fuelType,
            OffsetDateTime to,
            Pageable pageable
    );

    List<PriceObservationEntity> findByStationIdAndFuelTypeAndObservedAtBetweenOrderByObservedAtDescIdDesc(
            UUID stationId,
            String fuelType,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

}
