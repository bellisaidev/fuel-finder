package uk.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.fuelfinder.db.entity.PriceObservationEntity;

import java.util.UUID;

public interface PriceObservationRepository extends JpaRepository<PriceObservationEntity, UUID> {}
