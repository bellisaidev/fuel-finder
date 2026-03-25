package uk.co.fuelfinder.ingestion.raw.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPriceDto;
import uk.co.fuelfinder.ingestion.raw.client.dto.FuelPricesStationDto;
import uk.co.fuelfinder.ingestion.exception.FuelFinderAuthenticationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderConnectivityException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;

import java.util.List;

@Slf4j
@Component
public class FuelFinderFuelPricesClient {

    private static final String FUEL_PRICES_PATH = "/pfs/fuel-prices";

    private final WebClient fuelFinderApiWebClient;
    private final FuelFinderTokenProvider tokenProvider;

    public FuelFinderFuelPricesClient(
            @Qualifier("fuelFinderApiWebClient") WebClient fuelFinderApiWebClient,
            FuelFinderTokenProvider tokenProvider
    ) {
        this.fuelFinderApiWebClient = fuelFinderApiWebClient;
        this.tokenProvider = tokenProvider;
    }

    public List<FuelPricesStationDto> fetchFuelPrices(int batchNumber) {
        log.info("Fetching Fuel Finder fuel prices batch {}", batchNumber);

        try {
            List<FuelPricesStationDto> response = fuelFinderApiWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(FUEL_PRICES_PATH)
                            .queryParam("batch-number", batchNumber)
                            .build())
                    .headers(headers -> headers.setBearerAuth(tokenProvider.getAccessToken()))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<FuelPricesStationDto>>() {})
                    .block();

            if (response == null) {
                throw new FuelFinderInvalidResponseException(
                        "Fuel Finder fuel prices response is null for batch " + batchNumber
                );
            }

            log.info("Fuel Finder fuel prices batch {} fetched successfully: {} stations",
                    batchNumber, response.size());

            logSample(response);

            return response;
        } catch (FuelFinderAuthenticationException | FuelFinderConnectivityException | FuelFinderInvalidResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new FuelFinderIntegrationException(
                    "Unexpected error while fetching Fuel Finder fuel prices batch " + batchNumber, e
            );
        }
    }

    private void logSample(List<FuelPricesStationDto> stations) {
        stations.stream()
                .findFirst()
                .ifPresent(station -> {
                    String firstPriceSummary = station.fuelPrices() == null || station.fuelPrices().isEmpty()
                            ? "no fuel prices"
                            : formatFuelPrice(station.fuelPrices().get(0));

                    log.info(
                            "Fuel Finder fuel prices sample station: nodeId={}, tradingName={}, pricesCount={}, firstPrice={}",
                            station.nodeId(),
                            station.tradingName(),
                            station.fuelPrices() == null ? 0 : station.fuelPrices().size(),
                            firstPriceSummary
                    );
                });
    }

    private String formatFuelPrice(FuelPriceDto fuelPrice) {
        return "fuelType=%s, price=%s".formatted(
                fuelPrice.fuelType(),
                fuelPrice.price()
        );
    }
}