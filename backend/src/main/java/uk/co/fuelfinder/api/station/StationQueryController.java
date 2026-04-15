package uk.co.fuelfinder.api.station;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;

import java.util.List;

@RestController
@RequestMapping("/v1/stations")
@RequiredArgsConstructor
public class StationQueryController {

    private final StationQueryService stationQueryService;

    @GetMapping("/nearby")
    public List<NearbyStationResponse> getNearbyStations(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam double radiusMeters,
            @RequestParam String fuelType,
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
}
