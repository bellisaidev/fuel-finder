package uk.co.fuelfinder.ingestion.fetch;

import lombok.Builder;
import lombok.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
public class RetailerFeedClient {

    private final RestClient restClient;

    public RetailerFeedClient(RestClient.Builder builder) {
        this.restClient = builder
                .requestFactory(requestFactory(Duration.ofSeconds(5), Duration.ofSeconds(15)))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "fuel-finder/1.0")
                .build();
    }

    public FetchResult fetch(String url) {
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            return FetchResult.builder()
                    .httpStatus(200)
                    .body(body)
                    .error(null)
                    .build();

        } catch (RestClientResponseException e) {
            // HTTP status not 2xx + body possibly present
            return FetchResult.builder()
                    .httpStatus(e.getRawStatusCode())
                    .body(e.getResponseBodyAsString())
                    .error(e.getMessage())
                    .build();

        } catch (RestClientException e) {
            // timeouts, DNS, connection refused, etc.
            return FetchResult.builder()
                    .httpStatus(0)
                    .body(null)
                    .error(e.getMessage())
                    .build();
        }
    }

    @Value
    @Builder
    public static class FetchResult {
        int httpStatus;     // 0 = network error
        String body;        // may be null
        String error;       // may be null
    }

    private static org.springframework.http.client.ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return factory;
    }
}
