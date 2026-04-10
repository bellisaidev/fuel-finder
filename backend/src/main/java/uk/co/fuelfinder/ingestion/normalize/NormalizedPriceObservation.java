package uk.co.fuelfinder.ingestion.normalize;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class NormalizedPriceObservation {
    String siteId;
    String fuelType;
    BigDecimal price;
}