package uk.co.fuelfinder.api.station;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.fuelfinder.api.dto.ApiErrorResponse;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;

import java.util.List;

@RestController
@RequestMapping("/v1/stations")
@RequiredArgsConstructor
@Tag(name = "Stations", description = "Read endpoints for nearby fuel station discovery.")
public class StationQueryController {

    private final StationQueryService stationQueryService;

    @GetMapping("/nearby")
    @Operation(
            summary = "Find nearby stations",
            description = "Returns stations within the requested radius sorted primarily by distance and secondarily by price."
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
            @RequestParam double lat,
            @Parameter(description = "Longitude in decimal degrees.", example = "-0.1278")
            @RequestParam double lon,
            @Parameter(description = "Search radius in meters.", example = "5000")
            @RequestParam double radiusMeters,
            @Parameter(description = "Fuel type code to filter by.", example = "E5")
            @RequestParam String fuelType,
            @Parameter(description = "Maximum number of results. Defaults to 10, maximum 100.", example = "10")
            @RequestParam(required = false) Integer limit
    ) {
        return stationQueryService.findNearbyStations(
                lat,
                lon,
                radiusMeters,
                fuelType,
                limit
        );
    }

    @GetMapping("/cheapest-nearby")
    @Operation(
            summary = "Find the cheapest nearby stations",
            description = "Returns stations within the requested radius sorted primarily by price and secondarily by distance."
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
            @RequestParam double lat,
            @Parameter(description = "Longitude in decimal degrees.", example = "-0.1278")
            @RequestParam double lon,
            @Parameter(description = "Search radius in meters.", example = "5000")
            @RequestParam double radiusMeters,
            @Parameter(description = "Fuel type code to filter by.", example = "E5")
            @RequestParam String fuelType,
            @Parameter(description = "Maximum number of results. Defaults to 10, maximum 100.", example = "10")
            @RequestParam(required = false) Integer limit
    ) {
        return stationQueryService.findCheapestNearbyStations(
                lat,
                lon,
                radiusMeters,
                fuelType,
                limit
        );
    }
}
