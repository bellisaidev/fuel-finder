package uk.co.fuelfinder.ingestion.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.api.station.LatestPricesChangedEvent;
import uk.co.fuelfinder.persistence.repository.LatestPriceRepository;
import uk.co.fuelfinder.persistence.repository.PriceObservationRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatestPriceProjectionServiceTest {

    @Mock
    private LatestPriceRepository latestPriceRepository;

    @Mock
    private PriceObservationRepository priceObservationRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private LatestPriceProjectionService latestPriceProjectionService;

    @Test
    void backfillsWhenLatestPricesAreEmptyAndObservationsExist() {
        when(latestPriceRepository.count()).thenReturn(0L);
        when(priceObservationRepository.count()).thenReturn(5L);
        when(latestPriceRepository.backfillFromPriceObservations()).thenReturn(3);

        int inserted = latestPriceProjectionService.backfillIfEmpty();

        assertEquals(3, inserted);
        verify(latestPriceRepository).backfillFromPriceObservations();
        verify(applicationEventPublisher).publishEvent(new LatestPricesChangedEvent("latest-price-backfill"));
    }

    @Test
    void skipsBackfillWhenLatestPricesAlreadyExist() {
        when(latestPriceRepository.count()).thenReturn(2L);

        int inserted = latestPriceProjectionService.backfillIfEmpty();

        assertEquals(0, inserted);
        verify(priceObservationRepository, never()).count();
        verify(latestPriceRepository, never()).backfillFromPriceObservations();
        verify(applicationEventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
