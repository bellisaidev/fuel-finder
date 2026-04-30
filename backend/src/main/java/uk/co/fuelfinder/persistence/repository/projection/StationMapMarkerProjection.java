package uk.co.fuelfinder.persistence.repository.projection;

import java.util.UUID;

public interface StationMapMarkerProjection {

    UUID getStationId();

    String getSiteId();

    String getBrand();

    String getPostcode();

    Double getLatitude();

    Double getLongitude();

    String getFuelType();

    Integer getPricePence();
}
