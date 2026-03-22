package uk.co.fuelfinder.ingestion.fetch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record FuelPriceDto(
        @JsonProperty("fuel_type")
        String fuelType,

        @JsonProperty("price")
        BigDecimal price
) {
}