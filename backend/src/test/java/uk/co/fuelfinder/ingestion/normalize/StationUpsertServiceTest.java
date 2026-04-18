package uk.co.fuelfinder.ingestion.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import uk.co.fuelfinder.api.station.StationsChangedEvent;
import uk.co.fuelfinder.persistence.entity.RetailerEntity;
import uk.co.fuelfinder.persistence.entity.StationEntity;
import uk.co.fuelfinder.persistence.repository.StationRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationUpsertServiceTest {

    @Mock
    private StationRepository stationRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private StationUpsertService stationUpsertService;

    @Test
    void publishesStationsChangedEventWhenInsertingStation() {
        RetailerEntity retailer = retailer();
        NormalizedStation normalizedStation = normalizedStation("site-1");
        when(stationRepository.findByRetailerIdAndSiteId(retailer.getId(), "site-1")).thenReturn(Optional.empty());
        when(stationRepository.save(any(StationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int upserted = stationUpsertService.upsert(retailer, normalizedStation);

        assertEquals(1, upserted);
        verify(applicationEventPublisher).publishEvent(new StationsChangedEvent("station-upsert"));
    }

    @Test
    void publishesStationsChangedEventWhenUpdatingStation() {
        RetailerEntity retailer = retailer();
        NormalizedStation normalizedStation = normalizedStation("site-2");
        StationEntity existing = StationEntity.builder()
                .id(UUID.randomUUID())
                .retailer(retailer)
                .siteId("site-2")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(stationRepository.findByRetailerIdAndSiteId(retailer.getId(), "site-2")).thenReturn(Optional.of(existing));
        when(stationRepository.save(any(StationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int upserted = stationUpsertService.upsert(retailer, normalizedStation);

        assertEquals(1, upserted);
        verify(applicationEventPublisher).publishEvent(new StationsChangedEvent("station-upsert"));
    }

    private RetailerEntity retailer() {
        RetailerEntity retailer = new RetailerEntity();
        retailer.setId(UUID.randomUUID());
        retailer.setName("FUEL_FINDER_API");
        retailer.setCreatedAt(OffsetDateTime.now());
        retailer.setUpdatedAt(OffsetDateTime.now());
        return retailer;
    }

    private NormalizedStation normalizedStation(String siteId) {
        return NormalizedStation.builder()
                .siteId(siteId)
                .brand("Shell")
                .address("221B Baker Street")
                .city("London")
                .county("Greater London")
                .country("UK")
                .postcode("NW1 6XE")
                .latitude(51.5237)
                .longitude(-0.1585)
                .active(true)
                .build();
    }
}
