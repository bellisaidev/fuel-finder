package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.Builder;

@Builder
public record PfsStationProcessingSummary(
        int rawStationCount,
        int normalizedStationCount,
        int skippedCount,
        int skippedMissingSiteIdCount,
        int stationUpsertCount
) {
    public boolean normalizationReconciled() {
        return rawStationCount == normalizedStationCount + skippedCount;
    }
}
