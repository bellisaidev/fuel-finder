package uk.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.fuelfinder.db.entity.LatestPriceEntity;
import uk.fuelfinder.db.entity.LatestPriceId;

public interface LatestPriceRepository extends JpaRepository<LatestPriceEntity, LatestPriceId> {}
