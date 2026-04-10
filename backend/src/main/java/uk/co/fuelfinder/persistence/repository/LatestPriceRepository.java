package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uk.co.fuelfinder.persistence.entity.LatestPriceEntity;
import uk.co.fuelfinder.persistence.entity.LatestPriceId;

public interface LatestPriceRepository extends JpaRepository<LatestPriceEntity, LatestPriceId> {

    @Modifying
    @Query(value = """
            INSERT INTO latest_price (station_id, fuel_type, price_pence, currency, observed_at, reported_updated_at)
            SELECT DISTINCT ON (po.station_id, po.fuel_type)
                po.station_id,
                po.fuel_type,
                po.price_pence,
                po.currency,
                po.observed_at,
                po.reported_updated_at
            FROM price_observation po
            ORDER BY po.station_id, po.fuel_type, po.observed_at DESC, po.id DESC
            """, nativeQuery = true)
    int backfillFromPriceObservations();
}
