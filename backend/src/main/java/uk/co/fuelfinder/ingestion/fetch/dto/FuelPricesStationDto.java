package uk.co.fuelfinder.ingestion.fetch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record FuelPricesStationDto(
        @JsonProperty("node_id")
        String nodeId,

        @JsonProperty("trading_name")
        String tradingName,

        @JsonProperty("fuel_prices")
        List<FuelPriceDto> fuelPrices
) {
}