package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.fuelfinder.api.ApiRequestLogAttributes;
import uk.co.fuelfinder.api.ApiExceptionHandler;
import uk.co.fuelfinder.api.StationNotFoundException;
import uk.co.fuelfinder.api.station.dto.LatestStationPriceResponse;
import uk.co.fuelfinder.config.StationQueryApiLoggingConfig;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;
import uk.co.fuelfinder.api.station.dto.StationMapMarkerResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistoryResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceObservationResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryBucketResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StationQueryController.class)
@Import({ApiExceptionHandler.class, StationQueryApiLoggingConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class StationQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StationQueryService stationQueryService;

    @Test
    void returnsStationDetailsForValidStationId(CapturedOutput output) throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(stationQueryService.getStationDetails(stationId))
                .thenReturn(new StationDetailsResponse(
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
                        List.of(
                                new LatestStationPriceResponse(
                                        "E10",
                                        147,
                                        OffsetDateTime.parse("2026-04-18T10:15:30Z"),
                                        OffsetDateTime.parse("2026-04-18T10:10:00Z")
                                )
                        )
                ));

        mockMvc.perform(get("/v1/stations/{stationId}", stationId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stationId").value(stationId.toString()))
                .andExpect(jsonPath("$.latitude").value(51.5237))
                .andExpect(jsonPath("$.longitude").value(-0.1585))
                .andExpect(jsonPath("$.latestPrices[0].fuelType").value("E10"))
                .andExpect(jsonPath("$.latestPrices[0].pricePence").value(147))
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 1));

        assertLogContains(output, "event=station_query_completed");
        assertLogContains(output, "path=/v1/stations/" + stationId);
        assertLogContains(output, "status=200");
        assertLogContains(output, "resultCount=1");
    }

    @Test
    void returnsBadRequestForInvalidStationId() throws Exception {
        mockMvc.perform(get("/v1/stations/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("stationId has an invalid value"))
                .andExpect(request().attribute(ApiRequestLogAttributes.ERROR_MESSAGE, "stationId has an invalid value"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsNotFoundWhenStationDoesNotExist(CapturedOutput output) throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(stationQueryService.getStationDetails(stationId))
                .thenThrow(new StationNotFoundException(stationId));

        mockMvc.perform(get("/v1/stations/{stationId}", stationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Station not found: " + stationId))
                .andExpect(request().attribute(ApiRequestLogAttributes.ERROR_MESSAGE, "Station not found: " + stationId));

        assertLogContains(output, "event=station_query_not_found");
        assertLogContains(output, "path=/v1/stations/" + stationId);
        assertLogContains(output, "status=404");
        assertLogContains(output, "error=Station not found: " + stationId);
    }

    @Test
    void returnsStationPriceHistoryForValidRequest(CapturedOutput output) throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");

        when(stationQueryService.getStationPriceHistory(stationId, "E5", from, to, 100))
                .thenReturn(new StationPriceHistoryResponse(
                        stationId,
                        "E5",
                        from,
                        to,
                        List.of(new StationPriceObservationResponse(
                                145,
                                OffsetDateTime.parse("2026-04-18T10:15:30Z")
                        ))
                ));

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId)
                        .param("fuelType", "E5")
                        .param("from", "2026-04-18T00:00:00Z")
                        .param("to", "2026-04-19T00:00:00Z")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stationId").value(stationId.toString()))
                .andExpect(jsonPath("$.fuelType").value("E5"))
                .andExpect(jsonPath("$.observations[0].pricePence").value(145))
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 1));

        assertLogContains(output, "event=station_query_completed");
        assertLogContains(output, "path=/v1/stations/" + stationId + "/price-history");
        assertLogContains(output, "status=200");
        assertLogContains(output, "resultCount=1");
    }

    @Test
    void returnsBadRequestForMissingHistoryFuelType() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fuelType is required"))
                .andExpect(request().attribute(ApiRequestLogAttributes.ERROR_MESSAGE, "fuelType is required"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForBlankHistoryFuelType() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId)
                        .param("fuelType", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fuelType must not be blank"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForInvalidHistoryLimit() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId)
                        .param("fuelType", "E5")
                        .param("limit", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be less than or equal to 1000"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForInvalidHistoryFromFormat() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId)
                        .param("fuelType", "E5")
                        .param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from has an invalid value"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForHistoryRangeWhenFromIsAfterTo() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(stationQueryService.getStationPriceHistory(
                stationId,
                "E5",
                OffsetDateTime.parse("2026-04-19T00:00:00Z"),
                OffsetDateTime.parse("2026-04-18T00:00:00Z"),
                null
        )).thenThrow(new IllegalArgumentException("from must be less than or equal to to"));

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId)
                        .param("fuelType", "E5")
                        .param("from", "2026-04-19T00:00:00Z")
                        .param("to", "2026-04-18T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from must be less than or equal to to"));
    }

    @Test
    void returnsNotFoundForUnknownStationPriceHistory() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(stationQueryService.getStationPriceHistory(stationId, "E5", null, null, null))
                .thenThrow(new StationNotFoundException(stationId));

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId)
                        .param("fuelType", "E5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Station not found: " + stationId));
    }

    @Test
    void returnsEmptyObservationsWhenStationPriceHistoryHasNoMatches() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(stationQueryService.getStationPriceHistory(stationId, "E5", null, null, null))
                .thenReturn(new StationPriceHistoryResponse(
                        stationId,
                        "E5",
                        null,
                        null,
                        List.of()
                ));

        mockMvc.perform(get("/v1/stations/{stationId}/price-history", stationId)
                        .param("fuelType", "E5"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stationId").value(stationId.toString()))
                .andExpect(jsonPath("$.observations").isArray())
                .andExpect(jsonPath("$.observations").isEmpty())
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 0));
    }

    @Test
    void returnsStationPriceHistorySummaryForValidRequest(CapturedOutput output) throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        OffsetDateTime from = OffsetDateTime.parse("2026-04-18T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-19T00:00:00Z");

        when(stationQueryService.getStationPriceHistorySummary(stationId, "E5", from, to, 30))
                .thenReturn(new StationPriceHistorySummaryResponse(
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
                ));

        mockMvc.perform(get("/v1/stations/{stationId}/price-history/summary", stationId)
                        .param("fuelType", "E5")
                        .param("from", "2026-04-18T00:00:00Z")
                        .param("to", "2026-04-19T00:00:00Z")
                        .param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stationId").value(stationId.toString()))
                .andExpect(jsonPath("$.bucket").value("DAILY"))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.summaries[0].firstPricePence").value(149))
                .andExpect(jsonPath("$.summaries[0].lastPricePence").value(145))
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 1));

        assertLogContains(output, "event=station_query_completed");
        assertLogContains(output, "path=/v1/stations/" + stationId + "/price-history/summary");
        assertLogContains(output, "status=200");
        assertLogContains(output, "resultCount=1");
    }

    @Test
    void returnsBadRequestForInvalidHistorySummaryLimit() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        mockMvc.perform(get("/v1/stations/{stationId}/price-history/summary", stationId)
                        .param("fuelType", "E5")
                        .param("limit", "366"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be less than or equal to 365"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsNotFoundForUnknownStationPriceHistorySummary() throws Exception {
        UUID stationId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(stationQueryService.getStationPriceHistorySummary(stationId, "E5", null, null, null))
                .thenThrow(new StationNotFoundException(stationId));

        mockMvc.perform(get("/v1/stations/{stationId}/price-history/summary", stationId)
                        .param("fuelType", "E5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Station not found: " + stationId));
    }

    @Test
    void returnsStationMapMarkersForValidInBoundsRequest(CapturedOutput output) throws Exception {
        UUID stationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(stationQueryService.findStationsInBounds("-0.20,51.45,-0.05,51.55", "E5", 250))
                .thenReturn(List.of(new StationMapMarkerResponse(
                        stationId,
                        "SITE-1",
                        "Shell",
                        "NW1 6XE",
                        51.5237,
                        -0.1585,
                        "E5",
                        145
                )));

        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.20,51.45,-0.05,51.55")
                        .param("fuelType", "E5")
                        .param("limit", "250"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].stationId").value(stationId.toString()))
                .andExpect(jsonPath("$[0].siteId").value("SITE-1"))
                .andExpect(jsonPath("$[0].postcode").value("NW1 6XE"))
                .andExpect(jsonPath("$[0].latitude").value(51.5237))
                .andExpect(jsonPath("$[0].longitude").value(-0.1585))
                .andExpect(jsonPath("$[0].fuelType").value("E5"))
                .andExpect(jsonPath("$[0].pricePence").value(145))
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 1));

        assertLogContains(output, "event=station_query_completed");
        assertLogContains(output, "path=/v1/stations/in-bounds");
        assertLogContains(output, "status=200");
        assertLogContains(output, "resultCount=1");
    }

    @Test
    void usesDefaultInBoundsLimitWhenLimitIsOmitted() throws Exception {
        when(stationQueryService.findStationsInBounds("-0.20,51.45,-0.05,51.55", "E5", null))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.20,51.45,-0.05,51.55")
                        .param("fuelType", "E5"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(stationQueryService).findStationsInBounds("-0.20,51.45,-0.05,51.55", "E5", null);
    }

    @Test
    void returnsBadRequestForMissingInBoundsFuelType() throws Exception {
        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.20,51.45,-0.05,51.55"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fuelType is required"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForBlankInBoundsFuelType() throws Exception {
        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.20,51.45,-0.05,51.55")
                        .param("fuelType", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fuelType must not be blank"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForMalformedInBoundsBbox() throws Exception {
        when(stationQueryService.findStationsInBounds("-0.20,51.45,-0.05", "E5", null))
                .thenThrow(new IllegalArgumentException("bbox must contain west,south,east,north"));

        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.20,51.45,-0.05")
                        .param("fuelType", "E5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bbox must contain west,south,east,north"));
    }

    @Test
    void returnsBadRequestForNonNumericInBoundsBbox() throws Exception {
        when(stationQueryService.findStationsInBounds("-0.20,north,-0.05,51.55", "E5", null))
                .thenThrow(new IllegalArgumentException("bbox must contain numeric values"));

        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.20,north,-0.05,51.55")
                        .param("fuelType", "E5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bbox must contain numeric values"));
    }

    @Test
    void returnsBadRequestForOutOfRangeInBoundsBbox() throws Exception {
        when(stationQueryService.findStationsInBounds("-181,51.45,-0.05,51.55", "E5", null))
                .thenThrow(new IllegalArgumentException("bbox west must be between -180 and 180"));

        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-181,51.45,-0.05,51.55")
                        .param("fuelType", "E5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bbox west must be between -180 and 180"));
    }

    @Test
    void returnsBadRequestForInvertedInBoundsBbox() throws Exception {
        when(stationQueryService.findStationsInBounds("-0.05,51.45,-0.20,51.55", "E5", null))
                .thenThrow(new IllegalArgumentException("bbox west must be less than east"));

        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.05,51.45,-0.20,51.55")
                        .param("fuelType", "E5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bbox west must be less than east"));
    }

    @Test
    void returnsBadRequestForInvalidInBoundsLimit() throws Exception {
        mockMvc.perform(get("/v1/stations/in-bounds")
                        .param("bbox", "-0.20,51.45,-0.05,51.55")
                        .param("fuelType", "E5")
                        .param("limit", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be less than or equal to 500"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsEmptyArrayWhenNearbyQueryFindsNoStations(CapturedOutput output) throws Exception {
        when(stationQueryService.findNearbyStations(51.5074, -0.1278, 5000.0, "UNKNOWN", 10))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/stations/nearby")
                        .param("lat", "51.5074")
                        .param("lon", "-0.1278")
                        .param("radiusMeters", "5000")
                        .param("fuelType", "UNKNOWN")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"))
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 0));

        assertLogContains(output, "event=station_query_completed");
        assertLogContains(output, "path=/v1/stations/nearby");
        assertLogContains(output, "status=200");
        assertLogContains(output, "resultCount=0");
    }

    @Test
    void returnsEmptyArrayWhenCheapestNearbyQueryFindsNoStations() throws Exception {
        when(stationQueryService.findCheapestNearbyStations(0.0, 0.0, 1000.0, "E5", 10))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/stations/cheapest-nearby")
                        .param("lat", "0")
                        .param("lon", "0")
                        .param("radiusMeters", "1000")
                        .param("fuelType", "E5")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"))
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 0));
    }

    @Test
    void acceptsBoundaryValuesAndDelegatesToService() throws Exception {
        NearbyStationResponse response = new NearbyStationResponse(
                UUID.randomUUID(),
                "SITE-1",
                "Shell",
                "221B Baker Street",
                "London",
                "Greater London",
                "UK",
                "NW1 6XE",
                "E5",
                145,
                12.5
        );

        when(stationQueryService.findNearbyStations(-90.0, -180.0, 1.0, " e5 ", 100))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/v1/stations/nearby")
                        .param("lat", "-90")
                        .param("lon", "-180")
                        .param("radiusMeters", "1")
                        .param("fuelType", " e5 ")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fuelType").value("E5"));

        verify(stationQueryService).findNearbyStations(-90.0, -180.0, 1.0, " e5 ", 100);
    }

    @Test
    void usesDefaultLimitWhenLimitIsOmitted() throws Exception {
        when(stationQueryService.findNearbyStations(51.5074, -0.1278, 5000.0, "E5", null))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/stations/nearby")
                        .param("lat", "51.5074")
                        .param("lon", "-0.1278")
                        .param("radiusMeters", "5000")
                        .param("fuelType", "E5"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(stationQueryService).findNearbyStations(51.5074, -0.1278, 5000.0, "E5", null);
    }

    @Test
    void returnsBadRequestForLatitudeAboveRange(CapturedOutput output) throws Exception {
        mockMvc.perform(nearbyRequest("90.1", "-0.1278", "5000", "E5", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lat must be between -90 and 90"))
                .andExpect(request().attribute(ApiRequestLogAttributes.ERROR_MESSAGE, "lat must be between -90 and 90"));

        assertLogContains(output, "event=station_query_bad_request");
        assertLogContains(output, "status=400");
        assertLogContains(output, "error=lat must be between -90 and 90");

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForLatitudeBelowRange() throws Exception {
        mockMvc.perform(nearbyRequest("-90.1", "-0.1278", "5000", "E5", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lat must be between -90 and 90"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForLongitudeAboveRange() throws Exception {
        mockMvc.perform(nearbyRequest("51.5074", "180.1", "5000", "E5", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lon must be between -180 and 180"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForLongitudeBelowRange() throws Exception {
        mockMvc.perform(nearbyRequest("51.5074", "-180.1", "5000", "E5", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lon must be between -180 and 180"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForNonPositiveRadius() throws Exception {
        mockMvc.perform(nearbyRequest("51.5074", "-0.1278", "0", "E5", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("radiusMeters must be greater than 0"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForBlankFuelType() throws Exception {
        mockMvc.perform(nearbyRequest("51.5074", "-0.1278", "5000", "   ", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fuelType must not be blank"))
                .andExpect(request().attribute(ApiRequestLogAttributes.ERROR_MESSAGE, "fuelType must not be blank"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForNonPositiveLimit() throws Exception {
        mockMvc.perform(nearbyRequest("51.5074", "-0.1278", "5000", "E5", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be greater than 0"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForLimitAboveMaximum() throws Exception {
        mockMvc.perform(nearbyRequest("51.5074", "-0.1278", "5000", "E5", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be less than or equal to 100"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForMissingParameter() throws Exception {
        mockMvc.perform(get("/v1/stations/nearby")
                        .param("lat", "51.5074")
                        .param("lon", "-0.1278")
                        .param("radiusMeters", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("fuelType is required"))
                .andExpect(request().attribute(ApiRequestLogAttributes.ERROR_MESSAGE, "fuelType is required"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void returnsBadRequestForNonParseableParameter() throws Exception {
        mockMvc.perform(nearbyRequest("north", "-0.1278", "5000", "E5", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lat has an invalid value"))
                .andExpect(request().attribute(ApiRequestLogAttributes.ERROR_MESSAGE, "lat has an invalid value"));

        verifyNoInteractions(stationQueryService);
    }

    @Test
    void logsSuccessfulCheapestNearbyRequestWithResultCount(CapturedOutput output) throws Exception {
        when(stationQueryService.findCheapestNearbyStations(0.0, 0.0, 1000.0, "E5", 10))
                .thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/v1/stations/cheapest-nearby")
                        .param("lat", "0")
                        .param("lon", "0")
                        .param("radiusMeters", "1000")
                        .param("fuelType", "E5")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(request().attribute(ApiRequestLogAttributes.RESULT_COUNT, 1));

        assertLogContains(output, "event=station_query_completed");
        assertLogContains(output, "path=/v1/stations/cheapest-nearby");
        assertLogContains(output, "resultCount=1");
        org.junit.jupiter.api.Assertions.assertFalse(output.getOut().contains(" error="));
    }

    private NearbyStationResponse sampleResponse() {
        return new NearbyStationResponse(
                UUID.randomUUID(),
                "SITE-1",
                "Shell",
                "221B Baker Street",
                "London",
                "Greater London",
                "UK",
                "NW1 6XE",
                "E5",
                145,
                12.5
        );
    }

    private void assertLogContains(CapturedOutput output, String expected) {
        org.junit.jupiter.api.Assertions.assertTrue(
                output.getOut().contains(expected),
                () -> "Expected log output to contain '" + expected + "' but was:\n" + output.getOut()
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder nearbyRequest(
            String lat,
            String lon,
            String radiusMeters,
            String fuelType,
            String limit
    ) {
        return get("/v1/stations/nearby")
                .param("lat", lat)
                .param("lon", lon)
                .param("radiusMeters", radiusMeters)
                .param("fuelType", fuelType)
                .param("limit", limit);
    }
}
