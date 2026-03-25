package uk.co.fuelfinder.ingestion.raw.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PfsDayOpeningDto(
        String open,
        String close,

        @JsonProperty("is_24_hours")
        Boolean is24Hours
) {
}