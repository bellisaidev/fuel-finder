package uk.co.fuelfinder.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import uk.co.fuelfinder.ingestion.raw.orchestrator.RawIngestionSummary;
import uk.co.fuelfinder.ingestion.raw.orchestrator.ReconciliationStatus;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FuelFinderIngestionMetrics {

    static final String DURATION_NAME = "fuelfinder.ingestion.duration";
    static final String RECONCILIATION_NAME = "fuelfinder.ingestion.reconciliation";
    static final String STATIONS_PROCESSED_NAME = "fuelfinder.ingestion.stations.processed";
    static final String PRICES_PROCESSED_NAME = "fuelfinder.ingestion.prices.processed";
    static final String LAST_ATTEMPT_TIMESTAMP_NAME = "fuelfinder.ingestion.last.attempt.timestamp";
    static final String LAST_SUCCESS_TIMESTAMP_NAME = "fuelfinder.ingestion.last.success.timestamp";

    private static final String OUTCOME_TAG = "outcome";
    private static final String STATUS_TAG = "status";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry registry;
    private final Clock clock;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Map<ReconciliationStatus, Counter> reconciliationCounters;
    private final Counter processedStations;
    private final Counter processedPrices;
    private final AtomicLong lastAttemptTimestamp = new AtomicLong();
    private final AtomicLong lastSuccessTimestamp = new AtomicLong();

    public FuelFinderIngestionMetrics(MeterRegistry registry, Clock fuelFinderObservabilityClock) {
        this.registry = registry;
        this.clock = fuelFinderObservabilityClock;
        this.successTimer = Timer.builder(DURATION_NAME)
                .description("Complete Fuel Finder ingestion execution duration")
                .tag(OUTCOME_TAG, SUCCESS)
                .register(registry);
        this.failureTimer = Timer.builder(DURATION_NAME)
                .description("Complete Fuel Finder ingestion execution duration")
                .tag(OUTCOME_TAG, FAILURE)
                .register(registry);
        this.reconciliationCounters = buildReconciliationCounters(registry);
        this.processedStations = Counter.builder(STATIONS_PROCESSED_NAME)
                .description("Accepted and normalized station records")
                .register(registry);
        this.processedPrices = Counter.builder(PRICES_PROCESSED_NAME)
                .description("Accepted and normalized price observations")
                .register(registry);

        Gauge.builder(LAST_ATTEMPT_TIMESTAMP_NAME, lastAttemptTimestamp, AtomicLong::get)
                .description("Epoch timestamp of the last ingestion attempt")
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder(LAST_SUCCESS_TIMESTAMP_NAME, lastSuccessTimestamp, AtomicLong::get)
                .description("Epoch timestamp of the last successful ingestion")
                .baseUnit("seconds")
                .register(registry);
    }

    public IngestionAttempt startAttempt() {
        lastAttemptTimestamp.set(clock.instant().getEpochSecond());
        return new IngestionAttempt(Timer.start(registry));
    }

    public void recordCompletion(IngestionAttempt attempt, RawIngestionSummary summary) {
        Timer timer = summary.isSuccess() ? successTimer : failureTimer;
        attempt.sample().stop(timer);

        processedStations.increment(summary.getProcessedStationCount());
        processedPrices.increment(summary.getProcessedPriceCount());

        ReconciliationStatus reconciliationStatus = summary.getReconciliationStatus();
        if (reconciliationStatus != null) {
            reconciliationCounters.get(reconciliationStatus).increment();
        }

        if (summary.isSuccess()) {
            lastSuccessTimestamp.set(clock.instant().getEpochSecond());
        }
    }

    public void recordFailure(IngestionAttempt attempt) {
        attempt.sample().stop(failureTimer);
    }

    private Map<ReconciliationStatus, Counter> buildReconciliationCounters(MeterRegistry registry) {
        Map<ReconciliationStatus, Counter> counters = new EnumMap<>(ReconciliationStatus.class);
        for (ReconciliationStatus status : ReconciliationStatus.values()) {
            counters.put(status, Counter.builder(RECONCILIATION_NAME)
                    .description("Fuel Finder ingestion reconciliation outcomes")
                    .tag(STATUS_TAG, status.name().toLowerCase(Locale.ROOT))
                    .register(registry));
        }
        return counters;
    }

    public record IngestionAttempt(Timer.Sample sample) {
    }
}
