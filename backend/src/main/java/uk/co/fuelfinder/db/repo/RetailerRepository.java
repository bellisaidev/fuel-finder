package uk.co.fuelfinder.db.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.co.fuelfinder.db.entity.RetailerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetailerRepository extends JpaRepository<RetailerEntity, UUID> {
    Optional<RetailerEntity> findByName(String name);
    List<RetailerEntity> findAllByActiveTrue();
}
