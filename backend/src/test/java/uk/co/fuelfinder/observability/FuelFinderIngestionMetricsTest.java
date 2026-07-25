package uk.co.fuelfinder.observability;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.fuelfinder.ingestion.raw.orchestrator.RawIngestionSummary;
import uk.co.fuelfinder.ingestion.raw.orchestrator.ReconciliationStatus;
import uk.co.fuelfinder.observability.FuelFinderIngestionMetrics.IngestionAttempt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FuelFinderIngestionMetricsTest {

    private MockClock meterClock;
    private MutableClock timestampClock;
    private SimpleMeterRegistry registry;
    private FuelFinderIngestionMetrics metrics;

    @BeforeEach
    void setUp() {
        meterClock = new MockClock();
        timestampClock = new MutableClock(Instant.ofEpochSecond(1_000));
        registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, meterClock);
        metrics = new FuelFinderIngestionMetrics(registry, timestampClock);
    }

    @Test
    void registersCatalogueWithZeroInitialTimestamps() {
        assertThat(registry.find(FuelFinderIngestionMetrics.DURATION_NAME).timers()).hasSize(2);
        assertThat(registry.find(FuelFinderIngestionMetrics.RECONCILIATION_NAME).counters()).hasSize(3);
        assertThat(registry.find(FuelFinderIngestionMetrics.STATIONS_PROCESSED_NAME).counter()).isNotNull();
        assertThat(registry.find(FuelFinderIngestionMetrics.PRICES_PROCESSED_NAME).counter()).isNotNull();
        assertThat(registry.get(FuelFinderIngestionMetrics.LAST_ATTEMPT_TIMESTAMP_NAME).gauge().value())
                .isZero();
        assertThat(registry.get(FuelFinderIngestionMetrics.LAST_SUCCESS_TIMESTAMP_NAME).gauge().value())
                .isZero();
    }

    @Test
    void recordsSuccessfulCompletionDurationCountsStatusAndTimestamps() {
        IngestionAttempt attempt = metrics.startAttempt();
        meterClock.add(Duration.ofSeconds(4));
        timestampClock.advance(Duration.ofSeconds(5));

        metrics.recordCompletion(attempt, summary(true, ReconciliationStatus.OK_WITH_SKIPS, 12, 34));

        assertThat(registry.get(FuelFinderIngestionMetrics.DURATION_NAME)
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.get(FuelFinderIngestionMetrics.DURATION_NAME)
                .tag("outcome", "success")
                .timer()
                .totalTime(TimeUnit.SECONDS)).isEqualTo(4);
        assertThat(registry.get(FuelFinderIngestionMetrics.RECONCILIATION_NAME)
                .tag("status", "ok_with_skips")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(FuelFinderIngestionMetrics.STATIONS_PROCESSED_NAME).counter().count())
                .isEqualTo(12);
        assertThat(registry.get(FuelFinderIngestionMetrics.PRICES_PROCESSED_NAME).counter().count())
                .isEqualTo(34);
        assertThat(registry.get(FuelFinderIngestionMetrics.LAST_ATTEMPT_TIMESTAMP_NAME).gauge().value())
                .isEqualTo(1_000);
        assertThat(registry.get(FuelFinderIngestionMetrics.LAST_SUCCESS_TIMESTAMP_NAME).gauge().value())
                .isEqualTo(1_005);
    }

    @Test
    void recordsReturnedFailureWithoutUpdatingLastSuccess() {
        IngestionAttempt attempt = metrics.startAttempt();
        meterClock.add(Duration.ofSeconds(2));
        timestampClock.advance(Duration.ofSeconds(3));

        metrics.recordCompletion(attempt, summary(false, ReconciliationStatus.FAILED, 7, 9));

        assertThat(registry.get(FuelFinderIngestionMetrics.DURATION_NAME)
                .tag("outcome", "failure")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.get(FuelFinderIngestionMetrics.DURATION_NAME)
                .tag("outcome", "failure")
                .timer()
                .totalTime(TimeUnit.SECONDS)).isEqualTo(2);
        assertThat(registry.get(FuelFinderIngestionMetrics.RECONCILIATION_NAME)
                .tag("status", "failed")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(FuelFinderIngestionMetrics.LAST_SUCCESS_TIMESTAMP_NAME).gauge().value())
                .isZero();
    }

    @Test
    void registersEveryBoundedReconciliationStatusTag() {
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            IngestionAttempt attempt = metrics.startAttempt();
            meterClock.add(Duration.ofMillis(1));
            metrics.recordCompletion(attempt, summary(true, status, 0, 0));
        }

        assertThat(registry.get(FuelFinderIngestionMetrics.RECONCILIATION_NAME)
                .tag("status", "ok").counter().count()).isEqualTo(1);
        assertThat(registry.get(FuelFinderIngestionMetrics.RECONCILIATION_NAME)
                .tag("status", "ok_with_skips").counter().count()).isEqualTo(1);
        assertThat(registry.get(FuelFinderIngestionMetrics.RECONCILIATION_NAME)
                .tag("status", "failed").counter().count()).isEqualTo(1);
    }

    @Test
    void recordsThrownFailureWithoutReconciliationOrSuccessTimestamp() {
        IngestionAttempt attempt = metrics.startAttempt();
        meterClock.add(Duration.ofSeconds(1));

        metrics.recordFailure(attempt);

        assertThat(registry.get(FuelFinderIngestionMetrics.DURATION_NAME)
                .tag("outcome", "failure")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.find(FuelFinderIngestionMetrics.RECONCILIATION_NAME)
                .counters())
                .allMatch(counter -> counter.count() == 0);
        assertThat(registry.get(FuelFinderIngestionMetrics.LAST_SUCCESS_TIMESTAMP_NAME).gauge().value())
                .isZero();
    }

    private RawIngestionSummary summary(
            boolean success,
            ReconciliationStatus reconciliationStatus,
            int processedStations,
            int processedPrices
    ) {
        return RawIngestionSummary.builder()
                .retailerName("FUEL_FINDER_API")
                .startedAt(timestampClock.instant())
                .success(success)
                .processedStationCount(processedStations)
                .processedPriceCount(processedPrices)
                .reconciliationStatus(reconciliationStatus)
                .build();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
