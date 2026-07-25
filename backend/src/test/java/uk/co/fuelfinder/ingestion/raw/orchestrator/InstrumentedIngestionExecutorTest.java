package uk.co.fuelfinder.ingestion.raw.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.observability.FuelFinderIngestionMetrics;
import uk.co.fuelfinder.observability.FuelFinderIngestionMetrics.IngestionAttempt;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.repository.RetailerRepository;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentedIngestionExecutorTest {

    @Mock
    private RetailerRepository retailerRepository;

    @Mock
    private RetailerIngestionService retailerIngestionService;

    @Mock
    private FuelFinderIngestionMetrics metrics;

    private InstrumentedIngestionExecutor executor;
    private IngestionAttempt attempt;

    @BeforeEach
    void setUp() {
        executor = new InstrumentedIngestionExecutor(retailerRepository, retailerIngestionService, metrics);
        attempt = mock(IngestionAttempt.class);
        when(metrics.startAttempt()).thenReturn(attempt);
    }

    @Test
    void returnsSuccessfulSummaryAndRecordsCompletion() {
        RetailerEntity retailer = retailer();
        RawIngestionSummary summary = summary(true);
        when(retailerRepository.findByName("FUEL_FINDER_API")).thenReturn(Optional.of(retailer));
        when(retailerIngestionService.ingest(retailer)).thenReturn(summary);

        RawIngestionSummary result = executor.execute("FUEL_FINDER_API");

        assertThat(result).isSameAs(summary);
        verify(metrics).recordCompletion(attempt, summary);
        verify(metrics, never()).recordFailure(attempt);
    }

    @Test
    void recordsReturnedUnsuccessfulSummaryWithoutChangingIt() {
        RetailerEntity retailer = retailer();
        RawIngestionSummary summary = summary(false);
        when(retailerRepository.findByName("FUEL_FINDER_API")).thenReturn(Optional.of(retailer));
        when(retailerIngestionService.ingest(retailer)).thenReturn(summary);

        RawIngestionSummary result = executor.execute("FUEL_FINDER_API");

        assertThat(result).isSameAs(summary);
        verify(metrics).recordCompletion(attempt, summary);
        verify(metrics, never()).recordFailure(attempt);
    }

    @Test
    void recordsAndRethrowsRetailerLookupFailure() {
        when(retailerRepository.findByName("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.execute("MISSING"))
                .isInstanceOf(FuelFinderIntegrationException.class)
                .hasMessage("Retailer not found for ingestion: MISSING");

        verify(metrics).recordFailure(attempt);
        verify(metrics, never()).recordCompletion(any(), any());
        verify(retailerIngestionService, never()).ingest(any());
    }

    @Test
    void recordsAndRethrowsDelegatedFailureUnchanged() {
        RetailerEntity retailer = retailer();
        IllegalStateException failure = new IllegalStateException("unexpected");
        when(retailerRepository.findByName("FUEL_FINDER_API")).thenReturn(Optional.of(retailer));
        when(retailerIngestionService.ingest(retailer)).thenThrow(failure);

        assertThatThrownBy(() -> executor.execute("FUEL_FINDER_API")).isSameAs(failure);

        verify(metrics).recordFailure(attempt);
        verify(metrics, never()).recordCompletion(any(), any());
    }

    private RetailerEntity retailer() {
        RetailerEntity retailer = new RetailerEntity();
        retailer.setName("FUEL_FINDER_API");
        return retailer;
    }

    private RawIngestionSummary summary(boolean success) {
        return RawIngestionSummary.builder()
                .retailerName("FUEL_FINDER_API")
                .startedAt(Instant.EPOCH)
                .success(success)
                .build();
    }
}
