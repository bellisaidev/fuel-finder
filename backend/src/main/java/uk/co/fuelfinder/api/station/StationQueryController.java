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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.fuelfinder.api.ApiRequestLogAttributes;
import uk.co.fuelfinder.api.dto.ApiErrorResponse;
import uk.co.fuelfinder.api.station.dto.StationDetailsResponse;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/stations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Stations", description = "Read endpoints for nearby fuel station discovery.")
public class StationQueryController {

    private final StationQueryService stationQueryService;

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
