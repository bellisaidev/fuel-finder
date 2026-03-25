package uk.co.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.db.entity.RawFeedFetchEntity;

import java.util.UUID;

public interface RawFeedFetchRepository extends JpaRepository<RawFeedFetchEntity, UUID> {}
