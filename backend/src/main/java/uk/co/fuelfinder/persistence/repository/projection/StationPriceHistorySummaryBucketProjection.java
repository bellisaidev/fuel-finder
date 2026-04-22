package uk.co.fuelfinder.persistence.repository.projection;

import java.time.Instant;

public interface StationPriceHistorySummaryBucketProjection {

    Instant getBucketStart();

    Instant getBucketEnd();

    Integer getFirstPricePence();

    Integer getHighestPricePence();

    Integer getLowestPricePence();

    Integer getLastPricePence();

    Long getObservationCount();
}
