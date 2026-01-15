package uk.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.fuelfinder.db.entity.StationEntity;

import java.util.Optional;
import java.util.UUID;

public interface StationRepository extends JpaRepository<StationEntity, UUID> {
    Optional<StationEntity> findByRetailer_IdAndSiteId(UUID retailerId, String siteId);
}