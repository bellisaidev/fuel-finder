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
import uk.co.fuelfinder.config.StationQueryApiLoggingConfig;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;

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
