package uk.co.fuelfinder.api.station;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.fuelfinder.api.station.dto.NearbyStationResponse;
import uk.co.fuelfinder.persistence.repository.StationQueryRepository;
import uk.co.fuelfinder.persistence.repository.projection.NearbyStationProjection;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationQueryServiceTest {

    @Mock
    private StationQueryRepository stationQueryRepository;

    @InjectMocks
    private StationQueryService stationQueryService;

    @Test
    void returnsMappedNearbyStationsUsingNormalizedInputs() {
        UUID stationId = UUID.randomUUID();
        NearbyStationProjection projection = new TestNearbyStationProjection(
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

        when(stationQueryRepository.findNearbyStations(
                eq(51.5),
                eq(-0.1),
                eq(2000.0),
                eq("E10"),
                eq(10)
        )).thenReturn(List.of(projection));

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

        verify(stationQueryRepository).findNearbyStations(51.5, -0.1, 2000.0, "E10", 10);
    }

    @Test
    void returnsMappedCheapestNearbyStationsUsingPriceFirstOrderingQuery() {
        UUID stationId = UUID.randomUUID();
        NearbyStationProjection projection = new TestNearbyStationProjection(
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

        when(stationQueryRepository.findCheapestNearbyStations(
                eq(51.5074),
                eq(-0.1278),
                eq(5000.0),
                eq("E5"),
                eq(5)
        )).thenReturn(List.of(projection));

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

        verify(stationQueryRepository).findCheapestNearbyStations(51.5074, -0.1278, 5000.0, "E5", 5);
    }

    @Test
    void rejectsInvalidLatitude() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> stationQueryService.findNearbyStations(91.0, -0.1, 1000.0, "E10", 10)
        );

        assertEquals("lat must be between -90 and 90", exception.getMessage());
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

    private record TestNearbyStationProjection(
            UUID stationId,
            String siteId,
            String brand,
            String address,
            String city,
            String county,
            String country,
            String postcode,
            String fuelType,
            Integer pricePence,
            Double distanceMeters
    ) implements NearbyStationProjection {
        @Override
        public UUID getStationId() {
            return stationId;
        }

        @Override
        public String getSiteId() {
            return siteId;
        }

        @Override
        public String getBrand() {
            return brand;
        }

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public String getCity() {
            return city;
        }

        @Override
        public String getCounty() {
            return county;
        }

        @Override
        public String getCountry() {
            return country;
        }

        @Override
        public String getPostcode() {
            return postcode;
        }

        @Override
        public String getFuelType() {
            return fuelType;
        }

        @Override
        public Integer getPricePence() {
            return pricePence;
        }

        @Override
        public Double getDistanceMeters() {
            return distanceMeters;
        }
    }
}
