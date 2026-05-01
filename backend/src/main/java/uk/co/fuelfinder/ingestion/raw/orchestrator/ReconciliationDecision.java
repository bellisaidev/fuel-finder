package uk.co.fuelfinder.ingestion.raw.orchestrator;

import uk.co.fuelfinder.ingestion.normalize.FuelPriceProcessingSummary;

public record ReconciliationDecision(
        ReconciliationStatus status,
        ReconciliationAction action,
        boolean shouldAbort,
        String message
) {
    public static ReconciliationDecision evaluate(
            PfsStationProcessingSummary pfsSummary,
            FuelPriceProcessingSummary fuelPriceSummary,
            ReconciliationAction action
    ) {
        ReconciliationAction effectiveAction = action == null ? ReconciliationAction.FAIL : action;
        String message = buildMessage(pfsSummary, fuelPriceSummary);

        boolean failed = !pfsSummary.normalizationReconciled()
                || !fuelPriceSummary.normalizationReconciled()
                || !fuelPriceSummary.persistenceReconciled();

        if (failed) {
            return new ReconciliationDecision(
                    ReconciliationStatus.FAILED,
                    effectiveAction,
                    effectiveAction == ReconciliationAction.FAIL,
                    message
            );
        }

        boolean hasSkips = pfsSummary.skippedCount() > 0
                || fuelPriceSummary.skippedInvalidUnusableEntryCount() > 0
                || fuelPriceSummary.duplicateCount() > 0
                || fuelPriceSummary.missingStationCount() > 0
                || fuelPriceSummary.otherPersistenceSkipCount() > 0;

        return new ReconciliationDecision(
                hasSkips ? ReconciliationStatus.OK_WITH_SKIPS : ReconciliationStatus.OK,
                effectiveAction,
                false,
                message
        );
    }

    private static String buildMessage(
            PfsStationProcessingSummary pfsSummary,
            FuelPriceProcessingSummary fuelPriceSummary
    ) {
        return "pfsNormalization[rawStationCount=%d, normalizedStationCount=%d, skippedCount=%d]; "
                .formatted(
                        pfsSummary.rawStationCount(),
                        pfsSummary.normalizedStationCount(),
                        pfsSummary.skippedCount()
                )
                + "fuelPriceNormalization[rawFuelPriceEntryCount=%d, normalizedObservationCount=%d, skippedInvalidUnusableEntryCount=%d]; "
                .formatted(
                        fuelPriceSummary.rawFuelPriceEntryCount(),
                        fuelPriceSummary.normalizedObservationCount(),
                        fuelPriceSummary.skippedInvalidUnusableEntryCount()
                )
                + "fuelPricePersistence[normalizedObservationCount=%d, insertedCount=%d, duplicateCount=%d, missingStationCount=%d, otherPersistenceSkipCount=%d]"
                .formatted(
                        fuelPriceSummary.normalizedObservationCount(),
                        fuelPriceSummary.insertedCount(),
                        fuelPriceSummary.duplicateCount(),
                        fuelPriceSummary.missingStationCount(),
                        fuelPriceSummary.otherPersistenceSkipCount()
                );
    }
}
