package uk.co.fuelfinder.ingestion.raw.writer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

@Repository
public class LatestPriceJdbcRepository {

    private static final String DEFAULT_CURRENCY = "GBP";

    private final NamedParameterJdbcTemplate jdbc;

    public LatestPriceJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int upsert(UUID stationId, String fuelType, int pricePence, Instant observedAt) {
        String sql = """
            INSERT INTO latest_price (station_id, fuel_type, price_pence, currency, observed_at, reported_updated_at)
            VALUES (:station_id, :fuel_type, :price_pence, :currency, :observed_at, :reported_updated_at)
            ON CONFLICT (station_id, fuel_type)
            DO UPDATE SET
              price_pence = EXCLUDED.price_pence,
              currency = EXCLUDED.currency,
              observed_at = EXCLUDED.observed_at,
              reported_updated_at = EXCLUDED.reported_updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("station_id", stationId)
                .addValue("fuel_type", fuelType)
                .addValue("price_pence", pricePence)
                .addValue("currency", DEFAULT_CURRENCY)
                .addValue("observed_at", Timestamp.from(observedAt), Types.TIMESTAMP)
                .addValue("reported_updated_at", null, Types.TIMESTAMP);

        return jdbc.update(sql, params);
    }
}