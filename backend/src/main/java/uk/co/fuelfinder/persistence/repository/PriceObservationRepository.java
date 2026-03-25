package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;

import java.util.UUID;

public interface PriceObservationRepository extends JpaRepository<PriceObservationEntity, UUID> {}
