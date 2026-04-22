package uk.co.fuelfinder.api.station;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NormalizedStationPriceHistorySummaryQuery(
        UUID stationId,
        String fuelType,
        OffsetDateTime from,
        OffsetDateTime to,
        int limit
) {
}
