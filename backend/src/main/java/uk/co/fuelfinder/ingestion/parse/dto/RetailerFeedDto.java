package uk.co.fuelfinder.ingestion.parse.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetailerFeedDto {

    @JsonProperty("last_updated")
    private String lastUpdated;

    private List<StationDto> stations;
}
