package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.projection.NearbyStationProjection;

import java.util.List;
import java.util.UUID;

public interface StationQueryRepository extends Repository<StationEntity, UUID> {

    @Query(value = """
            SELECT
                s.id AS stationId,
                s.site_id AS siteId,
                s.brand AS brand,
                s.address AS address,
                s.city AS city,
                s.county AS county,
                s.country AS country,
                s.postcode AS postcode,
                lp.fuel_type AS fuelType,
                lp.price_pence AS pricePence,
                ST_Distance(
                    s.location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                ) AS distanceMeters
            FROM station s
            JOIN latest_price lp
              ON lp.station_id = s.id
            WHERE s.is_active = true
              AND s.location IS NOT NULL
              AND lp.fuel_type = :fuelType
              AND ST_DWithin(
                    s.location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :radiusMeters
              )
            ORDER BY distanceMeters ASC, lp.price_pence ASC, s.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyStationProjection> findNearbyStations(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            @Param("fuelType") String fuelType,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT
                s.id AS stationId,
                s.site_id AS siteId,
                s.brand AS brand,
                s.address AS address,
                s.city AS city,
                s.county AS county,
                s.country AS country,
                s.postcode AS postcode,
                lp.fuel_type AS fuelType,
                lp.price_pence AS pricePence,
                ST_Distance(
                    s.location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                ) AS distanceMeters
            FROM station s
            JOIN latest_price lp
              ON lp.station_id = s.id
            WHERE s.is_active = true
              AND s.location IS NOT NULL
              AND lp.fuel_type = :fuelType
              AND ST_DWithin(
                    s.location::geography,
                    ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                    :radiusMeters
              )
            ORDER BY lp.price_pence ASC, distanceMeters ASC, s.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<NearbyStationProjection> findCheapestNearbyStations(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            @Param("fuelType") String fuelType,
            @Param("limit") int limit
    );
}
