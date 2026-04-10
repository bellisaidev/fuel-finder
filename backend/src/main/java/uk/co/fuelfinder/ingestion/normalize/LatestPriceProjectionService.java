package uk.co.fuelfinder.ingestion.normalize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.fuelfinder.persistence.entity.LatestPriceEntity;
import uk.co.fuelfinder.persistence.entity.LatestPriceId;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.repository.LatestPriceRepository;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class LatestPriceProjectionService {

    private final LatestPriceRepository latestPriceRepository;
    private final PriceObservationRepository priceObservationRepository;

    @Transactional
    public void upsertFromObservation(PriceObservationEntity observation) {
        validateInput(observation);

        LatestPriceId id = new LatestPriceId(
                observation.getStation().getId(),
                observation.getFuelType()
        );

        LatestPriceEntity existing = latestPriceRepository.findById(id).orElse(null);

        if (existing == null) {
            LatestPriceEntity entity = LatestPriceEntity.builder()
                    .id(id)
                    .station(observation.getStation())
                    .pricePence(observation.getPricePence())
                    .currency(observation.getCurrency())
                    .observedAt(observation.getObservedAt())
                    .reportedUpdatedAt(null)
                    .build();

            latestPriceRepository.save(entity);

            log.debug(
                    "Inserted latest price: stationId={}, fuelType={}, pricePence={}",
                    observation.getStation().getId(),
                    observation.getFuelType(),
                    observation.getPricePence()
            );
            return;
        }

        if (observation.getObservedAt().isAfter(existing.getObservedAt())) {
            existing.setPricePence(observation.getPricePence());
            existing.setCurrency(observation.getCurrency());
            existing.setObservedAt(observation.getObservedAt());
            latestPriceRepository.save(existing);

            log.debug(
                    "Updated latest price: stationId={}, fuelType={}, pricePence={}",
                    observation.getStation().getId(),
                    observation.getFuelType(),
                    observation.getPricePence()
            );
        }
    }

    @Transactional
    public int backfillIfEmpty() {
        long latestPriceCount = latestPriceRepository.count();

        if (latestPriceCount > 0) {
            return 0;
        }

        long observationCount = priceObservationRepository.count();

        if (observationCount == 0) {
            return 0;
        }

        int inserted = latestPriceRepository.backfillFromPriceObservations();

        log.info(
                "Backfilled latest prices from observations: inserted={}, observationCount={}",
                inserted,
                observationCount
        );

        return inserted;
    }

    private void validateInput(PriceObservationEntity observation) {
        if (observation == null) {
            throw new IllegalArgumentException("PriceObservationEntity cannot be null");
        }

        if (observation.getStation() == null) {
            throw new IllegalArgumentException("PriceObservationEntity station cannot be null");
        }

        if (observation.getFuelType() == null || observation.getFuelType().isBlank()) {
            throw new IllegalArgumentException("PriceObservationEntity fuelType cannot be null or blank");
        }

        if (observation.getObservedAt() == null) {
            throw new IllegalArgumentException("PriceObservationEntity observedAt cannot be null");
        }
    }
}
