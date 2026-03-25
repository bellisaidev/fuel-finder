package uk.co.fuelfinder.ingestion.raw.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PfsLocationDto(
        @JsonProperty("address_line_1")
        String addressLine1,

        @JsonProperty("address_line_2")
        String addressLine2,

        String city,
        String country,
        String county,
        String postcode,
        Double latitude,
        Double longitude
) {
}