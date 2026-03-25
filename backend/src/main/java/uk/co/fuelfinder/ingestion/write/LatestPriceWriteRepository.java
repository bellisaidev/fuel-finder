package uk.co.fuelfinder.ingestion.write;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class LatestPriceWriteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public LatestPriceWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int upsert(UUID stationId, String fuelType, int pricePence, Instant observedAt, UUID priceObservationId) {
        String sql = """
            INSERT INTO latest_price (station_id, fuel_type, price_pence, observed_at, price_observation_id)
            VALUES (:station_id, :fuel_type, :price_pence, :observed_at, :price_observation_id)
            ON CONFLICT (station_id, fuel_type)
            DO UPDATE SET
              price_pence = EXCLUDED.price_pence,
              observed_at = EXCLUDED.observed_at,
              price_observation_id = EXCLUDED.price_observation_id
            """;

        return jdbc.update(sql, Map.of(
                "station_id", stationId,
                "fuel_type", fuelType,
                "price_pence", pricePence,
                "observed_at", observedAt,
                "price_observation_id", priceObservationId
        ));
    }
}
