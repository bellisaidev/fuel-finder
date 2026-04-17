package uk.co.fuelfinder.persistence.repository.projection;

import java.util.UUID;

public interface NearbyStationProjection {

    UUID getStationId();

    String getSiteId();

    String getBrand();

    String getAddress();

    String getCity();

    String getCounty();

    String getCountry();

    String getPostcode();

    String getFuelType();

    Integer getPricePence();

    Double getDistanceMeters();
}
