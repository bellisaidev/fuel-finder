package uk.co.fuelfinder.ingestion.normalize;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.co.fuelfinder.common.HashingUtils;
import uk.co.fuelfinder.persistence.entity.PriceObservationEntity;
import uk.co.fuelfinder.persistence.entity.RawFeedFetchEntity;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;
import uk.co.fuelfinder.persistence.repository.StationRepository;

import java.math.BigDecimal;
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

    @Transactional
    public int ingest(
            RetailerEntity retailer,
            RawFeedFetchEntity rawFeedFetch,
            List<?> fuelPricesStations
    ) {
        validateInput(retailer, rawFeedFetch, fuelPricesStations);

        int inserted = 0;

        for (Object item : fuelPricesStations) {
            @SuppressWarnings("unchecked")
            List<NormalizedPriceObservation> normalizedObservations =
                    fuelPricesNormalizer.normalize((uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto) item);

            for (NormalizedPriceObservation normalized : normalizedObservations) {
                StationEntity station = stationRepository.findByRetailerAndSiteId(retailer, normalized.getSiteId())
                        .orElse(null);

                if (station == null) {
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
                        normalized.getPrice().toPlainString(),
                        rawFeedFetch.getId()
                );

                if (priceObservationRepository.existsBySourceHash(sourceHash)) {
                    continue;
                }

                PriceObservationEntity entity = PriceObservationEntity.builder()
                        .id(UUID.randomUUID())
                        .station(station)
                        .fuelType(normalized.getFuelType())
                        .pricePence(toPence(normalized.getPrice()))
                        .currency(DEFAULT_CURRENCY)
                        .observedAt(OffsetDateTime.now())
                        .sourceHash(sourceHash)
                        .rawPayload(rawFeedFetch)
                        .build();

                priceObservationRepository.save(entity);
                inserted++;
            }
        }

        log.info(
                "Price observation ingestion completed: retailer={}, rawFeedFetchId={}, inserted={}",
                retailer.getName(),
                rawFeedFetch.getId(),
                inserted
        );

        return inserted;
    }

    private String buildSourceHash(
            UUID retailerId,
            String siteId,
            String fuelType,
            String price,
            UUID rawFeedFetchId
    ) {
        String fingerprint = "%s|%s|%s|%s|%s".formatted(
                retailerId,
                siteId,
                fuelType,
                price,
                rawFeedFetchId
        );

        return HashingUtils.sha256(fingerprint);
    }

    private void validateInput(
            RetailerEntity retailer,
            RawFeedFetchEntity rawFeedFetch,
            List<?> fuelPricesStations
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

    private int toPence(BigDecimal price) {
        return price
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValueExact();
    }
}