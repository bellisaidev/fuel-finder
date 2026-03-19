package uk.co.fuelfinder.ingestion;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class IngestionSummary {
    String retailerCode;
    Instant startedAt;
    boolean success;
    String failureReason;

    int stationUpserts;
    int observationsInserted;
    int latestUpserts;

    public static IngestionSummary success(String retailerCode, Instant startedAt,
                                           int stationUpserts, int observationsInserted, int latestUpserts) {
        return IngestionSummary.builder()
                .retailerCode(retailerCode)
                .startedAt(startedAt)
                .success(true)
                .failureReason(null)
                .stationUpserts(stationUpserts)
                .observationsInserted(observationsInserted)
                .latestUpserts(latestUpserts)
                .build();
    }

    public static IngestionSummary failed(String retailerCode, Instant startedAt, String reason) {
        return IngestionSummary.builder()
                .retailerCode(retailerCode)
                .startedAt(startedAt)
                .success(false)
                .failureReason(reason)
                .stationUpserts(0)
                .observationsInserted(0)
                .latestUpserts(0)
                .build();
    }
}
