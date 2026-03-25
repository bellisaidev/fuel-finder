package uk.co.fuelfinder.ingestion.raw.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderTokenProvider;
import uk.co.fuelfinder.ingestion.raw.client.dto.PfsStationDto;

import java.util.List;

@Slf4j
@Component
public class FuelFinderPfsClient {

    private final WebClient fuelFinderApiWebClient;
    private final FuelFinderTokenProvider tokenProvider;

    public FuelFinderPfsClient(
            @Qualifier("fuelFinderApiWebClient") WebClient fuelFinderApiWebClient,
            FuelFinderTokenProvider tokenProvider
    ) {
        this.fuelFinderApiWebClient = fuelFinderApiWebClient;
        this.tokenProvider = tokenProvider;
    }

    public List<PfsStationDto> fetchBatch(int batchNumber) {
        String accessToken = tokenProvider.getAccessToken();

        log.info("Fetching Fuel Finder PFS batch {}", batchNumber);

        List<PfsStationDto> stations = fuelFinderApiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/pfs")
                        .queryParam("batch-number", batchNumber)
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> new IllegalStateException(
                                        "Fuel Finder PFS request failed: status="
                                                + response.statusCode()
                                                + ", body=" + body))
                )
                .bodyToMono(new ParameterizedTypeReference<List<PfsStationDto>>() {})
                .block();

        if (stations == null) {
            throw new IllegalStateException("Fuel Finder PFS response was null");
        }

        log.info("Fuel Finder PFS batch {} fetched successfully: {} stations", batchNumber, stations.size());
        return stations;
    }
}