package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.persistence.entity.LatestPriceEntity;
import uk.co.fuelfinder.persistence.entity.LatestPriceId;

public interface LatestPriceRepository extends JpaRepository<LatestPriceEntity, LatestPriceId> {}
