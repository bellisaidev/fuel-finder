package uk.co.fuelfinder.ingestion.fetch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PfsBankHolidayOpeningDto(
        String type,

        @JsonProperty("open_time")
        String openTime,

        @JsonProperty("close_time")
        String closeTime,

        @JsonProperty("is_24_hours")
        Boolean is24Hours
) {
}