package uk.co.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.db.entity.StationEntity;

import java.util.Optional;
import java.util.UUID;

public interface StationRepository extends JpaRepository<StationEntity, UUID> {
    Optional<StationEntity> findByRetailer_IdAndSiteId(UUID retailerId, String siteId);
}