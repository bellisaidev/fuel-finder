package uk.co.fuelfinder.ingestion.fetch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PfsOpeningTimesDto(
        @JsonProperty("usual_days")
        PfsUsualDaysDto usualDays,

        @JsonProperty("bank_holiday")
        PfsBankHolidayOpeningDto bankHoliday
) {
}