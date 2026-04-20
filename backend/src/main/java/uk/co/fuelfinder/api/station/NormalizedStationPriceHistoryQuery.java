package uk.co.fuelfinder.api.station;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NormalizedStationPriceHistoryQuery(
        UUID stationId,
        String fuelType,
        OffsetDateTime from,
        OffsetDateTime to,
        int limit
) {
}
