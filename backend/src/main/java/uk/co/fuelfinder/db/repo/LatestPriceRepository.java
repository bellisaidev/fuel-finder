package uk.co.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.db.entity.LatestPriceEntity;
import uk.co.fuelfinder.db.entity.LatestPriceId;

public interface LatestPriceRepository extends JpaRepository<LatestPriceEntity, LatestPriceId> {}
