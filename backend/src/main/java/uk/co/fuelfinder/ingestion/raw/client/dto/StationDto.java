package uk.co.fuelfinder.ingestion.raw.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StationDto {

    @JsonProperty("site_id")
    private String siteId;

    private String brand;
    private String address;
    private String postcode;

    // tipicamente "latitude" / "longitude" nel feed interim
    private Double latitude;
    private Double longitude;

    // es: {"E10":1459,"B7":1529}
    private Map<String, Integer> prices;
}
