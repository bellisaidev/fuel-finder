package uk.co.fuelfinder.ingestion.raw.client.dto;

public record PfsUsualDaysDto(
        PfsDayOpeningDto monday,
        PfsDayOpeningDto tuesday,
        PfsDayOpeningDto wednesday,
        PfsDayOpeningDto thursday,
        PfsDayOpeningDto friday,
        PfsDayOpeningDto saturday,
        PfsDayOpeningDto sunday
) {
}