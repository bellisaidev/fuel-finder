package uk.co.fuelfinder.api.station;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.fuelfinder.api.ApiRequestLogAttributes;
import uk.co.fuelfinder.api.dto.ApiErrorResponse;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.api.station.dto.StationMapMarkerResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistoryResponse;
import uk.co.fuelfinder.api.station.dto.StationPriceHistorySummaryResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/stations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Stations", description = "Read endpoints for nearby fuel station discovery.")
public class StationQueryController {

    private final StationQueryService stationQueryService;

    @GetMapping("/in-bounds")
    @Operation(
            summary = "Find stations in map viewport",
            description = "Returns active station markers inside the requested bounding box for the requested fuel type. The bbox format is west,south,east,north."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Stations in bounds found successfully.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = StationMapMarkerResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameters.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public List<StationMapMarkerResponse> getStationsInBounds(
            @Parameter(description = "Bounding box as west,south,east,north.", example = "-0.20,51.45,-0.05,51.55")
            @NotBlank(message = "bbox must not be blank")
            @RequestParam String bbox,
            @Parameter(description = "Fuel type code to filter by.", example = "E5")
            @NotBlank(message = "fuelType must not be blank")
            @RequestParam String fuelType,
            @Parameter(description = "Maximum number of results. Defaults to 250, maximum 500.", example = "250")
            @Positive(message = "limit must be greater than 0")
            @Max(value = 500, message = "limit must be less than or equal to 500")
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        List<StationMapMarkerResponse> response = stationQueryService.findStationsInBounds(
                bbox,
                fuelType,
                limit
        );
        request.setAttribute(ApiRequestLogAttributes.RESULT_COUNT, response.size());
        return response;
    }

    @GetMapping("/{stationId}")
    @Operation(
            summary = "Get station details",
            description = "Returns the full public details for a single station identified by its UUID, including all latest available prices by fuel type."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Station details found successfully.",
                    content = @Content(schema = @Schema(implementation = StationDetailsResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid station identifier.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Station not found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public StationDetailsResponse getStationDetails(
            @Parameter(description = "Internal station UUID.", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID stationId,
            HttpServletRequest request
    ) {
        StationDetailsResponse response = stationQueryService.getStationDetails(stationId);
        request.setAttribute(ApiRequestLogAttributes.RESULT_COUNT, 1);
        return response;
    }

    @GetMapping("/{stationId}/price-history")
    @Operation(
            summary = "Get station price history",
            description = "Returns historical price observations for a single station and fuel type, ordered from newest to oldest."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Station price history found successfully.",
                    content = @Content(schema = @Schema(implementation = StationPriceHistoryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid station identifier or query parameters.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Station not found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public StationPriceHistoryResponse getStationPriceHistory(
            @Parameter(description = "Internal station UUID.", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID stationId,
            @Parameter(description = "Fuel type code to filter by.", example = "E5")
            @NotBlank(message = "fuelType must not be blank")
            @RequestParam String fuelType,
            @Parameter(description = "Inclusive lower bound for observedAt in ISO-8601 format.", example = "2026-04-18T00:00:00Z")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,
            @Parameter(description = "Inclusive upper bound for observedAt in ISO-8601 format.", example = "2026-04-19T00:00:00Z")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to,
            @Parameter(description = "Maximum number of observations. Defaults to 100, maximum 1000.", example = "100")
            @Positive(message = "limit must be greater than 0")
            @Max(value = 1000, message = "limit must be less than or equal to 1000")
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        StationPriceHistoryResponse response = stationQueryService.getStationPriceHistory(
                stationId,
                fuelType,
                from,
                to,
                limit
        );
        request.setAttribute(ApiRequestLogAttributes.RESULT_COUNT, response.observations().size());
        return response;
    }

    @GetMapping("/{stationId}/price-history/summary")
    @Operation(
            summary = "Get station price history summary",
            description = "Returns daily summarized historical price observations for a single station and fuel type, ordered from newest bucket to oldest."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Station price history summary found successfully.",
                    content = @Content(schema = @Schema(implementation = StationPriceHistorySummaryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid station identifier or query parameters.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Station not found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public StationPriceHistorySummaryResponse getStationPriceHistorySummary(
            @Parameter(description = "Internal station UUID.", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID stationId,
            @Parameter(description = "Fuel type code to filter by.", example = "E5")
            @NotBlank(message = "fuelType must not be blank")
            @RequestParam String fuelType,
            @Parameter(description = "Inclusive lower bound for observedAt in ISO-8601 format.", example = "2026-04-18T00:00:00Z")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime from,
            @Parameter(description = "Inclusive upper bound for observedAt in ISO-8601 format.", example = "2026-04-19T00:00:00Z")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            @RequestParam(required = false) OffsetDateTime to,
            @Parameter(description = "Maximum number of daily buckets. Defaults to 30, maximum 365.", example = "30")
            @Positive(message = "limit must be greater than 0")
            @Max(value = 365, message = "limit must be less than or equal to 365")
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        StationPriceHistorySummaryResponse response = stationQueryService.getStationPriceHistorySummary(
                stationId,
                fuelType,
                from,
                to,
                limit
        );
        request.setAttribute(ApiRequestLogAttributes.RESULT_COUNT, response.summaries().size());
        return response;
    }

    @GetMapping("/nearby")
    @Operation(
            summary = "Find nearby stations",
            description = "Returns stations within the requested radius sorted primarily by distance and secondarily by price. Returns an empty array when no stations match the query."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Nearby stations found successfully.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NearbyStationResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameters.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public List<NearbyStationResponse> getNearbyStations(
            @Parameter(description = "Latitude in decimal degrees.", example = "51.5074")
            @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
            @RequestParam double lat,
            @Parameter(description = "Longitude in decimal degrees.", example = "-0.1278")
            @DecimalMin(value = "-180.0", message = "lon must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "lon must be between -180 and 180")
            @RequestParam double lon,
            @Parameter(description = "Search radius in meters.", example = "5000")
            @Positive(message = "radiusMeters must be greater than 0")
            @RequestParam double radiusMeters,
            @Parameter(description = "Fuel type code to filter by.", example = "E5")
            @NotBlank(message = "fuelType must not be blank")
            @RequestParam String fuelType,
            @Parameter(description = "Maximum number of results. Defaults to 10, maximum 100.", example = "10")
            @Positive(message = "limit must be greater than 0")
            @Max(value = 100, message = "limit must be less than or equal to 100")
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        List<NearbyStationResponse> response = stationQueryService.findNearbyStations(
                lat,
                lon,
                radiusMeters,
                fuelType,
                limit
        );
        request.setAttribute(ApiRequestLogAttributes.RESULT_COUNT, response.size());
        return response;
    }

    @GetMapping("/cheapest-nearby")
    @Operation(
            summary = "Find the cheapest nearby stations",
            description = "Returns stations within the requested radius sorted primarily by price and secondarily by distance. Returns an empty array when no stations match the query."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Cheapest nearby stations found successfully.",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NearbyStationResponse.class)))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid query parameters.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    public List<NearbyStationResponse> getCheapestNearbyStations(
            @Parameter(description = "Latitude in decimal degrees.", example = "51.5074")
            @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
            @RequestParam double lat,
            @Parameter(description = "Longitude in decimal degrees.", example = "-0.1278")
            @DecimalMin(value = "-180.0", message = "lon must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "lon must be between -180 and 180")
            @RequestParam double lon,
            @Parameter(description = "Search radius in meters.", example = "5000")
            @Positive(message = "radiusMeters must be greater than 0")
            @RequestParam double radiusMeters,
            @Parameter(description = "Fuel type code to filter by.", example = "E5")
            @NotBlank(message = "fuelType must not be blank")
            @RequestParam String fuelType,
            @Parameter(description = "Maximum number of results. Defaults to 10, maximum 100.", example = "10")
            @Positive(message = "limit must be greater than 0")
            @Max(value = 100, message = "limit must be less than or equal to 100")
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        List<NearbyStationResponse> response = stationQueryService.findCheapestNearbyStations(
                lat,
                lon,
                radiusMeters,
                fuelType,
                limit
        );
        request.setAttribute(ApiRequestLogAttributes.RESULT_COUNT, response.size());
        return response;
    }
}
