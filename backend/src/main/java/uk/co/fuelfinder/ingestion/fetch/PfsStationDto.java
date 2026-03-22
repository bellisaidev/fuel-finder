package uk.co.fuelfinder.ingestion.fetch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PfsStationDto(
        @JsonProperty("node_id")
        String nodeId,

        @JsonProperty("public_phone_number")
        String publicPhoneNumber,

        @JsonProperty("trading_name")
        String tradingName,

        @JsonProperty("is_same_trading_and_brand_name")
        Boolean sameTradingAndBrandName,

        @JsonProperty("brand_name")
        String brandName,

        @JsonProperty("temporary_closure")
        Boolean temporaryClosure,

        @JsonProperty("permanent_closure")
        Boolean permanentClosure,

        @JsonProperty("permanent_closure_date")
        String permanentClosureDate,

        @JsonProperty("is_motorway_service_station")
        Boolean motorwayServiceStation,

        @JsonProperty("is_supermarket_service_station")
        Boolean supermarketServiceStation,

        @JsonProperty("location")
        PfsLocationDto location,

        @JsonProperty("amenities")
        List<String> amenities,

        @JsonProperty("opening_times")
        PfsOpeningTimesDto openingTimes,

        @JsonProperty("fuel_types")
        List<String> fuelTypes
) {
}