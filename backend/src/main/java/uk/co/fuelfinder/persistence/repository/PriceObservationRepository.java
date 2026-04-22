package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.repository.projection.StationPriceHistorySummaryBucketProjection;

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

    @Query(value = """
            WITH filtered AS (
                SELECT
                    po.id,
                    po.price_pence,
                    po.observed_at,
                    (date_trunc('day', po.observed_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC') AS bucket_start
                FROM price_observation po
                WHERE po.station_id = :stationId
                  AND po.fuel_type = :fuelType
                  AND (CAST(:from AS timestamptz) IS NULL OR po.observed_at >= CAST(:from AS timestamptz))
                  AND (CAST(:to AS timestamptz) IS NULL OR po.observed_at <= CAST(:to AS timestamptz))
            ),
            ranked AS (
                SELECT
                    bucket_start,
                    price_pence,
                    ROW_NUMBER() OVER (PARTITION BY bucket_start ORDER BY observed_at ASC, id ASC) AS first_rank,
                    ROW_NUMBER() OVER (PARTITION BY bucket_start ORDER BY observed_at DESC, id DESC) AS last_rank
                FROM filtered
            )
            SELECT
                bucket_start AS bucketStart,
                bucket_start + INTERVAL '1 day' AS bucketEnd,
                MAX(CASE WHEN first_rank = 1 THEN price_pence END) AS firstPricePence,
                MAX(price_pence) AS highestPricePence,
                MIN(price_pence) AS lowestPricePence,
                MAX(CASE WHEN last_rank = 1 THEN price_pence END) AS lastPricePence,
                COUNT(*) AS observationCount
            FROM ranked
            GROUP BY bucket_start
            ORDER BY bucket_start DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<StationPriceHistorySummaryBucketProjection> findDailySummaryByStationIdAndFuelType(
            @Param("stationId") UUID stationId,
            @Param("fuelType") String fuelType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("limit") int limit
    );

}
