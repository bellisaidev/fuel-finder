package uk.co.fuelfinder.ingestion.normalize;

import lombok.Builder;

@Builder
public record FuelPriceProcessingSummary(
        int rawStationCount,
        int rawFuelPriceEntryCount,
        int normalizedObservationCount,
        int skippedInvalidUnusableEntryCount,
        int insertedCount,
        int duplicateCount,
        int missingStationCount,
        int otherPersistenceSkipCount
) {
    public boolean normalizationReconciled() {
        return rawFuelPriceEntryCount == normalizedObservationCount + skippedInvalidUnusableEntryCount;
    }

    public boolean persistenceReconciled() {
        return normalizedObservationCount == insertedCount
                + duplicateCount
                + missingStationCount
                + otherPersistenceSkipCount;
    }
}
