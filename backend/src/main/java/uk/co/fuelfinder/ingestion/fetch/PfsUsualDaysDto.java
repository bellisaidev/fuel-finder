package uk.co.fuelfinder.ingestion.fetch;

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