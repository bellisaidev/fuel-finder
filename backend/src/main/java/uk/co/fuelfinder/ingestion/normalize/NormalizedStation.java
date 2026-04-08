package uk.co.fuelfinder.ingestion.normalize;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NormalizedStation {
    String siteId;
    String brand;
    String address;
    String postcode;
    Double latitude;
    Double longitude;
    boolean active;
}