package uk.co.fuelfinder.ingestion.raw.orchestrator;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class RawIngestionSummary {

    String retailerName;
    Instant startedAt;
    boolean success;
    String failureReason;

    int pfsBatchNumber;
    int pfsRecordCount;
    UUID pfsRawFeedFetchId;

    int fuelPricesBatchNumber;
    int fuelPricesRecordCount;
    UUID fuelPricesRawFeedFetchId;

    ReconciliationStatus reconciliationStatus;
    ReconciliationAction reconciliationAction;
    boolean reconciliationShouldAbort;
    String reconciliationMessage;

    public static RawIngestionSummary success(
            String retailerName,
            Instant startedAt,
            int pfsBatchNumber,
            int pfsRecordCount,
            UUID pfsRawFeedFetchId,
            int fuelPricesBatchNumber,
            int fuelPricesRecordCount,
            UUID fuelPricesRawFeedFetchId,
            ReconciliationDecision reconciliationDecision
    ) {
        return RawIngestionSummary.builder()
                .retailerName(retailerName)
                .startedAt(startedAt)
                .success(true)
                .failureReason(null)
                .pfsBatchNumber(pfsBatchNumber)
                .pfsRecordCount(pfsRecordCount)
                .pfsRawFeedFetchId(pfsRawFeedFetchId)
                .fuelPricesBatchNumber(fuelPricesBatchNumber)
                .fuelPricesRecordCount(fuelPricesRecordCount)
                .fuelPricesRawFeedFetchId(fuelPricesRawFeedFetchId)
                .reconciliationStatus(reconciliationDecision.status())
                .reconciliationAction(reconciliationDecision.action())
                .reconciliationShouldAbort(reconciliationDecision.shouldAbort())
                .reconciliationMessage(reconciliationDecision.message())
                .build();
    }

    public static RawIngestionSummary failed(
            String retailerName,
            Instant startedAt,
            String reason
    ) {
        return failed(retailerName, startedAt, reason, null);
    }

    public static RawIngestionSummary failed(
            String retailerName,
            Instant startedAt,
            String reason,
            ReconciliationDecision reconciliationDecision
    ) {
        return RawIngestionSummary.builder()
                .retailerName(retailerName)
                .startedAt(startedAt)
                .success(false)
                .failureReason(reason)
                .pfsBatchNumber(0)
                .pfsRecordCount(0)
                .pfsRawFeedFetchId(null)
                .fuelPricesBatchNumber(0)
                .fuelPricesRecordCount(0)
                .fuelPricesRawFeedFetchId(null)
                .reconciliationStatus(reconciliationDecision == null ? null : reconciliationDecision.status())
                .reconciliationAction(reconciliationDecision == null ? null : reconciliationDecision.action())
                .reconciliationShouldAbort(reconciliationDecision != null && reconciliationDecision.shouldAbort())
                .reconciliationMessage(reconciliationDecision == null ? null : reconciliationDecision.message())
                .build();
    }
}
