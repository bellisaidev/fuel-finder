package uk.co.fuelfinder.ingestion.normalize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.fuelfinder.api.station.LatestPricesChangedEvent;
import uk.co.fuelfinder.api.station.PriceObservationsChangedEvent;
import uk.co.fuelfinder.common.HashingUtils;
import uk.co.fuelfinder.common.PriceUtils;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceObservationIngestionService {

    private static final String DEFAULT_CURRENCY = "GBP";

    private final FuelPricesNormalizer fuelPricesNormalizer;
    private final StationRepository stationRepository;
    private final PriceObservationRepository priceObservationRepository;
    private final LatestPriceProjectionService latestPriceProjectionService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public FuelPriceProcessingSummary ingest(
            RetailerEntity retailer,
            RawFeedFetchEntity rawFeedFetch,
            List<FuelPricesStationDto> fuelPricesStations
    ) {
        validateInput(retailer, rawFeedFetch, fuelPricesStations);

        int rawFuelPriceEntryCount = 0;
        int normalizedObservationCount = 0;
        int inserted = 0;
        int skippedMissingStation = 0;
        int skippedDuplicate = 0;

        for (FuelPricesStationDto stationDto : fuelPricesStations) {
            rawFuelPriceEntryCount += stationDto.fuelPrices() == null ? 0 : stationDto.fuelPrices().size();
            List<NormalizedPriceObservation> normalizedObservations = fuelPricesNormalizer.normalize(stationDto);
            normalizedObservationCount += normalizedObservations.size();

            for (NormalizedPriceObservation normalized : normalizedObservations) {
                StationEntity station = stationRepository.findByRetailerAndSiteId(retailer, normalized.getSiteId())
                        .orElse(null);

                if (station == null) {
                    skippedMissingStation++;
                    log.warn(
                            "Skipping price observation because station was not found: retailer={}, siteId={}, fuelType={}",
                            retailer.getName(),
                            normalized.getSiteId(),
                            normalized.getFuelType()
                    );
                    continue;
                }

                String sourceHash = buildSourceHash(
                        retailer.getId(),
                        normalized.getSiteId(),
                        normalized.getFuelType(),
                        normalized.getPrice().toPlainString()
                );

                if (priceObservationRepository.existsBySourceHash(sourceHash)) {
                    skippedDuplicate++;
                    continue;
                }

                PriceObservationEntity entity = PriceObservationEntity.builder()
                        .id(UUID.randomUUID())
                        .station(station)
                        .fuelType(normalized.getFuelType())
                        .pricePence(PriceUtils.toPence(normalized.getPrice()))
                        .currency(DEFAULT_CURRENCY)
                        .observedAt(OffsetDateTime.now())
                        .sourceHash(sourceHash)
                        .rawPayload(rawFeedFetch)
                        .build();

                PriceObservationEntity saved = priceObservationRepository.save(entity);
                latestPriceProjectionService.upsertFromObservation(saved);
                inserted++;
            }
        }

        int skippedInvalidUnusableEntries = Math.max(rawFuelPriceEntryCount - normalizedObservationCount, 0);
        FuelPriceProcessingSummary summary = FuelPriceProcessingSummary.builder()
                .rawStationCount(fuelPricesStations.size())
                .rawFuelPriceEntryCount(rawFuelPriceEntryCount)
                .normalizedObservationCount(normalizedObservationCount)
                .skippedInvalidUnusableEntryCount(skippedInvalidUnusableEntries)
                .insertedCount(inserted)
                .duplicateCount(skippedDuplicate)
                .missingStationCount(skippedMissingStation)
                .otherPersistenceSkipCount(0)
                .build();

        log.info(
                "Price observation ingestion completed: retailer={}, rawFeedFetchId={}, rawStationCount={}, rawFuelPriceEntryCount={}, normalizedObservationCount={}, skippedInvalidUnusableEntryCount={}, inserted={}, skippedDuplicate={}, skippedMissingStation={}, otherPersistenceSkipCount={}",
                retailer.getName(),
                rawFeedFetch.getId(),
                summary.rawStationCount(),
                summary.rawFuelPriceEntryCount(),
                summary.normalizedObservationCount(),
                summary.skippedInvalidUnusableEntryCount(),
                inserted,
                skippedDuplicate,
                skippedMissingStation,
                summary.otherPersistenceSkipCount()
        );

        if (inserted > 0) {
            applicationEventPublisher.publishEvent(new PriceObservationsChangedEvent("price-observation-ingestion"));
            applicationEventPublisher.publishEvent(new LatestPricesChangedEvent("price-observation-ingestion"));
        }

        return summary;
    }

    private String buildSourceHash(
            UUID retailerId,
            String siteId,
            String fuelType,
            String price
    ) {
        String fingerprint = "%s|%s|%s|%s".formatted(
                retailerId,
                siteId,
                fuelType,
                price
        );

        return HashingUtils.sha256(fingerprint);
    }

    private void validateInput(
            RetailerEntity retailer,
            RawFeedFetchEntity rawFeedFetch,
            List<FuelPricesStationDto> fuelPricesStations
    ) {
        if (retailer == null) {
            throw new IllegalArgumentException("Retailer cannot be null");
        }

        if (rawFeedFetch == null) {
            throw new IllegalArgumentException("RawFeedFetchEntity cannot be null");
        }

        if (fuelPricesStations == null) {
            throw new IllegalArgumentException("Fuel prices stations cannot be null");
        }
    }
}
