package uk.co.fuelfinder.ingestion.raw.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import uk.co.fuelfinder.ingestion.exception.FuelFinderInvalidResponseException;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpExceptionMapper;
import uk.co.fuelfinder.ingestion.raw.http.FuelFinderHttpResilience;

import java.util.List;

@Slf4j
@Component
public class FuelFinderPfsClient {

    private final WebClient fuelFinderApiWebClient;
    private final FuelFinderTokenProvider tokenProvider;
    private final FuelFinderHttpResilience resilience;
    private final FuelFinderHttpExceptionMapper exceptionMapper;

    public FuelFinderPfsClient(
            @Qualifier("fuelFinderApiWebClient") WebClient fuelFinderApiWebClient,
            FuelFinderTokenProvider tokenProvider,
            FuelFinderHttpResilience resilience,
            FuelFinderHttpExceptionMapper exceptionMapper
    ) {
        this.fuelFinderApiWebClient = fuelFinderApiWebClient;
        this.tokenProvider = tokenProvider;
        this.resilience = resilience;
        this.exceptionMapper = exceptionMapper;
    }

    public List<PfsStationDto> fetchBatch(int batchNumber) {
        String accessToken = tokenProvider.getAccessToken();

        log.info("Fetching Fuel Finder PFS batch {}", batchNumber);

        List<PfsStationDto> stations;
        try {
            stations = resilience.execute(fuelFinderApiWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/pfs")
                            .queryParam("batch-number", batchNumber)
                            .build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            response -> FuelFinderWebClientErrors.mapBatchError(response, batchNumber)
                    )
                    .bodyToMono(new ParameterizedTypeReference<List<PfsStationDto>>() {}))
                    .block();
        } catch (FuelFinderBatchUnavailableException e) {
            log.info("Fuel Finder PFS batch {} is not available; treating it as end of feed", batchNumber);
            return List.of();
        } catch (RuntimeException e) {
            throw exceptionMapper.mapApiFailure("Fuel Finder PFS batch " + batchNumber + " request", e);
        }

        if (stations == null) {
            throw new FuelFinderInvalidResponseException("Fuel Finder PFS response was null");
        }

        log.info("Fuel Finder PFS batch {} fetched successfully: {} stations", batchNumber, stations.size());
        return stations;
    }

}
