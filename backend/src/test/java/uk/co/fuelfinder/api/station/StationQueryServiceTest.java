package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.api.StationNotFoundException;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.api.station.dto.StationMapMarkerResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistoryResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceObservationResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryBucketResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryResponse;

import java.time.OffsetDateTime;
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
    void returnsStationsInBoundsUsingParsedBboxAndNormalizedInputs() {
        UUID stationId = UUID.randomUUID();
        StationMapMarkerResponse expected = new StationMapMarkerResponse(
                stationId,
                "SITE-1",
                "Shell",
                "NW1 6XE",
                51.5237,
                -0.1585,
                "E5",
                145
        );

        when(cachedStationQueryService.findStationsInBounds(argThat(query ->
                query.west() == -0.20
                        && query.south() == 51.45
                        && query.east() == -0.05
                        && query.north() == 51.55
                        && query.fuelType().equals("E5")
                        && query.limit() == 250
        ))).thenReturn(List.of(expected));

        List<StationMapMarkerResponse> response = stationQueryService.findStationsInBounds(
                " -0.20, 51.45, -0.05, 51.55 ",
                " e5 ",
                null
        );

        assertEquals(1, response.size());
        assertEquals(stationId, response.getFirst().stationId());
        assertEquals(51.5237, response.getFirst().latitude());
        assertEquals(-0.1585, response.getFirst().longitude());
        assertEquals("E5", response.getFirst().fuelType());

        verify(cachedStationQueryService).findStationsInBounds(
                new NormalizedStationBoundsQuery(-0.20, 51.45, -0.05, 51.55, "E5", 250)
        );
    }

    @Test
    void preservesExplicitInBoundsLimit() {
        when(cachedStationQueryService.findStationsInBounds(argThat(query ->
                query.limit() == 500
        ))).thenReturn(List.of());

        stationQueryService.findStationsInBounds("-0.20,51.45,-0.05,51.55", "E5", 500);

        verify(cachedStationQueryService).findStationsInBounds(
                new NormalizedStationBoundsQuery(-0.20, 51.45, -0.05, 51.55, "E5", 500)
        );
    }

    @Test
    void rejectsMalformedInBoundsBbox() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findStationsInBounds("-0.20,51.45,-0.05", "E5", null)
        );

        assertEquals("bbox must contain west,south,east,north", exception.getMessage());
    }

    @Test
    void rejectsNonNumericInBoundsBbox() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findStationsInBounds("-0.20,north,-0.05,51.55", "E5", null)
        );

        assertEquals("bbox must contain numeric values", exception.getMessage());
    }

    @Test
    void rejectsOutOfRangeInBoundsBbox() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findStationsInBounds("-181,51.45,-0.05,51.55", "E5", null)
        );

        assertEquals("bbox west must be between -180 and 180", exception.getMessage());
    }

    @Test
    void rejectsInvertedInBoundsLongitudeRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findStationsInBounds("-0.05,51.45,-0.20,51.55", "E5", null)
        );

        assertEquals("bbox west must be less than east", exception.getMessage());
    }

    @Test
    void rejectsInvertedInBoundsLatitudeRange() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findStationsInBounds("-0.20,51.55,-0.05,51.45", "E5", null)
        );

        assertEquals("bbox south must be less than north", exception.getMessage());
    }

    @Test
    void rejectsInBoundsLimitAboveMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findStationsInBounds("-0.20,51.45,-0.05,51.55", "E5", 501)
        );

        assertEquals("limit must be less than or equal to 500", exception.getMessage());
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

    @Test
    void returnsStationDetailsWithSortedLatestPrices() {
        UUID stationId = UUID.randomUUID();
        StationDetailsResponse expected = new StationDetailsResponse(
                stationId,
                "SITE-1",
                "Shell",
                "221B Baker Street",
                "London",
                "Greater London",
                "UK",
                "NW1 6XE",
                51.5237,
                -0.1585,
                List.of()
        );
        when(cachedStationQueryService.getStationDetails(stationId)).thenReturn(expected);

        StationDetailsResponse response = stationQueryService.getStationDetails(stationId);

        assertEquals(stationId, response.stationId());
        assertEquals(51.5237, response.latitude());
        assertEquals(-0.1585, response.longitude());
        verify(cachedStationQueryService).getStationDetails(stationId);
    }

    @Test
    void returnsStationDetailsWithEmptyLatestPricesWhenNoPricesExist() {
        UUID stationId = UUID.randomUUID();
        StationDetailsResponse expected = new StationDetailsResponse(
                stationId,
                "SITE-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
        when(cachedStationQueryService.getStationDetails(stationId)).thenReturn(expected);

        StationDetailsResponse response = stationQueryService.getStationDetails(stationId);

        assertEquals(stationId, response.stationId());
        assertEquals(List.of(), response.latestPrices());
        verify(cachedStationQueryService).getStationDetails(stationId);
    }

    @Test
    void throwsStationNotFoundWhenStationDoesNotExist() {
        UUID stationId = UUID.randomUUID();
        when(cachedStationQueryService.getStationDetails(stationId)).thenThrow(new StationNotFoundException(stationId));

        StationNotFoundException exception = assertThrows(
                StationNotFoundException.class,
                () -> stationQueryService.getStationDetails(stationId)
        );

        assertEquals("Station not found: " + stationId, exception.getMessage());
        verify(cachedStationQueryService).getStationDetails(stationId);
    }

    @Test
    void returnsStationPriceHistoryUsingNormalizedInputs() {
        UUID stationId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");
        StationPriceHistoryResponse expected = new StationPriceHistoryResponse(
                stationId,
                "E5",
                from,
                to,
                List.of(new StationPriceObservationResponse(145, OffsetDateTime.parse("2026-04-18T10:15:30Z")))
        );

        when(cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(
                stationId,
                "E5",
                from,
                to,
                100
        ))).thenReturn(expected);

        StationPriceHistoryResponse response = stationQueryService.getStationPriceHistory(
                stationId,
                " e5 ",
                from,
                to,
                null
        );

        assertEquals(stationId, response.stationId());
        assertEquals("E5", response.fuelType());
        assertEquals(1, response.observations().size());
        verify(cachedStationQueryService).getStationPriceHistory(new NormalizedStationPriceHistoryQuery(
                stationId,
                "E5",
                from,
                to,
                100
        ));
    }

    @Test
    void rejectsHistoryLimitAboveMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.getStationPriceHistory(
                        UUID.randomUUID(),
                        "E5",
                        null,
                        null,
                        1001
                )
        );

        assertEquals("limit must be less than or equal to 1000", exception.getMessage());
    }

    @Test
    void rejectsHistoryRangeWhenFromIsAfterTo() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.getStationPriceHistory(
                        UUID.randomUUID(),
                        "E5",
                        OffsetDateTime.parse("2026-04-19T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-18T00:00:00Z"),
                        100
                )
        );

        assertEquals("from must be less than or equal to to", exception.getMessage());
    }

    @Test
    void preservesInactiveStationHistoryLookupByDelegatingWithoutActiveFilter() {
        UUID stationId = UUID.randomUUID();
        StationPriceHistoryResponse expected = new StationPriceHistoryResponse(
                stationId,
                "E5",
                null,
                null,
                List.of()
        );
        when(cachedStationQueryService.getStationPriceHistory(new NormalizedStationPriceHistoryQuery(
                stationId,
                "E5",
                null,
                null,
                100
        ))).thenReturn(expected);

        StationPriceHistoryResponse response = stationQueryService.getStationPriceHistory(
                stationId,
                "E5",
                null,
                null,
                null
        );

        assertEquals(stationId, response.stationId());
        verify(cachedStationQueryService).getStationPriceHistory(new NormalizedStationPriceHistoryQuery(
                stationId,
                "E5",
                null,
                null,
                100
        ));
    }

    @Test
    void returnsStationPriceHistorySummaryUsingNormalizedInputs() {
        UUID stationId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");
        StationPriceHistorySummaryResponse expected = new StationPriceHistorySummaryResponse(
                stationId,
                "E5",
                from,
                to,
                "DAILY",
                "UTC",
                List.of(new StationPriceHistorySummaryBucketResponse(
                        OffsetDateTime.parse("2026-04-18T00:00:00Z"),
                        OffsetDateTime.parse("2026-04-19T00:00:00Z"),
                        149,
                        151,
                        145,
                        145,
                        3
                ))
        );

        when(cachedStationQueryService.getStationPriceHistorySummary(new NormalizedStationPriceHistorySummaryQuery(
                stationId,
                "E5",
                from,
                to,
                30
        ))).thenReturn(expected);

        StationPriceHistorySummaryResponse response = stationQueryService.getStationPriceHistorySummary(
                stationId,
                " e5 ",
                from,
                to,
                null
        );

        assertEquals(stationId, response.stationId());
        assertEquals("E5", response.fuelType());
        assertEquals("DAILY", response.bucket());
        assertEquals(1, response.summaries().size());
        verify(cachedStationQueryService).getStationPriceHistorySummary(new NormalizedStationPriceHistorySummaryQuery(
                stationId,
                "E5",
                from,
                to,
                30
        ));
    }

    @Test
    void rejectsHistorySummaryLimitAboveMaximum() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.getStationPriceHistorySummary(
                        UUID.randomUUID(),
                        "E5",
                        null,
                        null,
                        366
                )
        );

        assertEquals("limit must be less than or equal to 365", exception.getMessage());
    }
}
