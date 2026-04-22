package uk.co.fuelfinder.api.station.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Daily summarized price bucket for a station fuel type.")
public record StationPriceHistorySummaryBucketResponse(
        @Schema(description = "Inclusive start of the summary bucket in UTC.", example = "2026-04-18T00:00:00Z")
        OffsetDateTime bucketStart,
        @Schema(description = "Exclusive end of the summary bucket in UTC.", example = "2026-04-19T00:00:00Z")
        OffsetDateTime bucketEnd,
        @Schema(description = "First recorded price in the bucket.", example = "149")
        int firstPricePence,
        @Schema(description = "Highest recorded price in the bucket.", example = "151")
        int highestPricePence,
        @Schema(description = "Lowest recorded price in the bucket.", example = "145")
        int lowestPricePence,
        @Schema(description = "Last recorded price in the bucket.", example = "145")
        int lastPricePence,
        @Schema(description = "Number of observations included in the bucket.", example = "3")
        long observationCount
) {
}
