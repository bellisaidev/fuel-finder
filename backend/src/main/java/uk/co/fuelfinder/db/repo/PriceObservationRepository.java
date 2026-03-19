package uk.co.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.db.entity.PriceObservationEntity;

import java.util.UUID;

public interface PriceObservationRepository extends JpaRepository<PriceObservationEntity, UUID> {}
