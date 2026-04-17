package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationQueryServiceTest {

    @Mock
    private CachedStationQueryService cachedStationQueryService;

    @InjectMocks
    private StationQueryService stationQueryService;

    @Test
    void returnsNearbyStationsUsingNormalizedInputs() {
        UUID stationId = UUID.randomUUID();
        NearbyStationResponse expected = new NearbyStationResponse(
                stationId,
                "SITE-1",
                "Shell",
                "221B Baker Street",
                "London",
                "Greater London",
                "UK",
                "NW1 6XE",
                "E10",
                145,
                321.5
        );

        when(cachedStationQueryService.findNearbyStations(argThat(query ->
                query.lat() == 51.5
                        && query.lon() == -0.1
                        && query.radiusMeters() == 2000.0
                        && query.fuelType().equals("E10")
                        && query.limit() == 10
        ))).thenReturn(List.of(expected));

        List<NearbyStationResponse> response = stationQueryService.findNearbyStations(
                51.5,
                -0.1,
                2000.0,
                " e10 ",
                null
        );

        assertEquals(1, response.size());
        assertEquals(stationId, response.getFirst().stationId());
        assertEquals("London", response.getFirst().city());
        assertEquals("E10", response.getFirst().fuelType());
        assertEquals(145, response.getFirst().pricePence());
        assertEquals(321.5, response.getFirst().distanceMeters());

        verify(cachedStationQueryService).findNearbyStations(new NormalizedStationQuery(51.5, -0.1, 2000.0, "E10", 10));
    }

    @Test
    void returnsCheapestNearbyStationsUsingPriceFirstOrderingQuery() {
        UUID stationId = UUID.randomUUID();
        NearbyStationResponse expected = new NearbyStationResponse(
                stationId,
                "SITE-2",
                "BP",
                "1 Victoria Street",
                "London",
                "Greater London",
                "UK",
                "SW1H 0ET",
                "E5",
                139,
                950.0
        );

        when(cachedStationQueryService.findCheapestNearbyStations(argThat(query ->
                query.lat() == 51.5074
                        && query.lon() == -0.1278
                        && query.radiusMeters() == 5000.0
                        && query.fuelType().equals("E5")
                        && query.limit() == 5
        ))).thenReturn(List.of(expected));

        List<NearbyStationResponse> response = stationQueryService.findCheapestNearbyStations(
                51.5074,
                -0.1278,
                5000.0,
                "e5",
                5
        );

        assertEquals(1, response.size());
        assertEquals(stationId, response.getFirst().stationId());
        assertEquals("BP", response.getFirst().brand());
        assertEquals(139, response.getFirst().pricePence());
        assertEquals(950.0, response.getFirst().distanceMeters());

        verify(cachedStationQueryService).findCheapestNearbyStations(
                new NormalizedStationQuery(51.5074, -0.1278, 5000.0, "E5", 5)
        );
    }

    @Test
    void preservesCoordinatesAndRadiusWhileNormalizingFuelTypeAndDefaultLimit() {
        when(cachedStationQueryService.findNearbyStations(argThat(query ->
                query.lat() == 51.5074001
                        && query.lon() == -0.1278001
                        && query.radiusMeters() == 2000.4
                        && query.fuelType().equals("E5")
                        && query.limit() == 10
        ))).thenReturn(List.of());

        stationQueryService.findNearbyStations(
                51.5074001,
                -0.1278001,
                2000.4,
                " e5 ",
                null
        );

        verify(cachedStationQueryService).findNearbyStations(
                new NormalizedStationQuery(51.5074001, -0.1278001, 2000.4, "E5", 10)
        );
    }

    @Test
    void rejectsBlankFuelType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findNearbyStations(51.5, -0.1, 1000.0, "   ", 10)
        );

        assertEquals("fuelType must not be blank", exception.getMessage());
    }

    @Test
    void rejectsLimitAboveMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findNearbyStations(51.5, -0.1, 1000.0, "E10", 101)
        );

        assertEquals("limit must be less than or equal to 100", exception.getMessage());
    }
}
