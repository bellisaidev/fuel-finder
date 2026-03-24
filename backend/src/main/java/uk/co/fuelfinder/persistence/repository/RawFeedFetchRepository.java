package uk.co.fuelfinder.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;

import java.util.UUID;

public interface RawFeedFetchRepository extends JpaRepository<RawFeedFetchEntity, UUID> {}
