package uk.co.fuelfinder.ingestion.normalize;

import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPriceDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class FuelPricesNormalizer {

    public List<NormalizedPriceObservation> normalize(FuelPricesStationDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("FuelPricesStationDto cannot be null");
        }

        List<NormalizedPriceObservation> normalized = new ArrayList<>();

        if (dto.fuelPrices() == null || dto.fuelPrices().isEmpty()) {
            return normalized;
        }

        String siteId = trimToNull(dto.nodeId());

        for (FuelPriceDto fuelPrice : dto.fuelPrices()) {
            if (fuelPrice == null) {
                continue;
            }

            String fuelType = trimToNull(fuelPrice.fuelType());
            BigDecimal price = fuelPrice.price();

            if (siteId == null || fuelType == null || price == null) {
                continue;
            }

            normalized.add(NormalizedPriceObservation.builder()
                    .siteId(siteId)
                    .fuelType(fuelType)
                    .price(price)
                    .build());
        }

        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}