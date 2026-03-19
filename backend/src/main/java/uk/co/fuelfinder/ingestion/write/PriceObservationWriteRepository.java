package uk.co.fuelfinder.ingestion.write;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Repository
public class PriceObservationWriteRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PriceObservationWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return number of rows inserted (0 or 1)
     */
    public int insertIfNotExists(UUID id,
                                 UUID stationId,
                                 String fuelType,
                                 int pricePence,
                                 Instant observedAt,
                                 String sourceHash) {

        String sql = """
            INSERT INTO price_observation (id, station_id, fuel_type, price_pence, observed_at, source_hash)
            VALUES (:id, :station_id, :fuel_type, :price_pence, :observed_at, :source_hash)
            ON CONFLICT (source_hash) DO NOTHING
            """;

        return jdbc.update(sql, Map.of(
                "id", id,
                "station_id", stationId,
                "fuel_type", fuelType,
                "price_pence", pricePence,
                "observed_at", observedAt,
                "source_hash", sourceHash
        ));
    }
}
