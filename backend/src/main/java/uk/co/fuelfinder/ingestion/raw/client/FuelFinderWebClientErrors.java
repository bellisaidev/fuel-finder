package uk.co.fuelfinder.ingestion.raw.client;

import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

final class FuelFinderWebClientErrors {

    private FuelFinderWebClientErrors() {
    }

    static Mono<? extends Throwable> mapBatchError(ClientResponse response, int batchNumber) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    if (body.contains("Requested batch " + batchNumber + " is not available")) {
                        return new FuelFinderBatchUnavailableException(body);
                    }

                    return WebClientResponseException.create(
                            response.statusCode().value(),
                            response.statusCode().toString(),
                            response.headers().asHttpHeaders(),
                            body.getBytes(StandardCharsets.UTF_8),
                            StandardCharsets.UTF_8
                    );
                });
    }
}
