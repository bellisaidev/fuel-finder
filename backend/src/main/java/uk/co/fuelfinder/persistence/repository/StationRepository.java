package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.persistence.entity.StationEntity;

import java.util.Optional;
import java.util.UUID;

public interface StationRepository extends JpaRepository<StationEntity, UUID> {
    Optional<StationEntity> findByRetailer_IdAndSiteId(UUID retailerId, String siteId);
}