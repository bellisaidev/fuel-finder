package uk.co.fuelfinder.ingestion.raw.writer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

@Repository
public class PriceObservationJdbcRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PriceObservationJdbcRepository(NamedParameterJdbcTemplate jdbc) {
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
            ON CONFLICT (station_id, fuel_type, source_hash) DO NOTHING
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("station_id", stationId)
                .addValue("fuel_type", fuelType)
                .addValue("price_pence", pricePence)
                .addValue("observed_at", Timestamp.from(observedAt), Types.TIMESTAMP)
                .addValue("source_hash", sourceHash);

        return jdbc.update(sql, params);
    }
}