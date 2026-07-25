package uk.co.fuelfinder.ingestion.raw.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FuelFinderHttpResilienceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void retriesTransientHttpStatuses(int status) {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));

        Mono<String> request = Mono.defer(() -> attempts.incrementAndGet() == 1
                ? Mono.error(responseException(status, null))
                : Mono.just("ok"));

        StepVerifier.withVirtualTime(() -> resilience.execute(request))
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("ok")
                .verifyComplete();

        assertThat(attempts).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 501, 505})
    void doesNotRetryPermanentHttpStatuses(int status) {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));

        StepVerifier.withVirtualTime(() -> resilience.execute(Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(responseException(status, null));
                })))
                .expectError(WebClientResponseException.class)
                .verify();

        assertThat(attempts).hasValue(1);
    }

    @Test
    void retriesTransportFailureAndStopsAfterConfiguredAttemptCount() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));

        StepVerifier.withVirtualTime(() -> resilience.execute(Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(requestException(new IOException("connection reset")));
                })))
                .thenAwait(Duration.ofSeconds(3))
                .expectError(WebClientRequestException.class)
                .verify();

        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryTlsFailures() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));

        StepVerifier.withVirtualTime(() -> resilience.execute(Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(requestException(new SSLHandshakeException("certificate rejected")));
                })))
                .expectError(WebClientRequestException.class)
                .verify();

        assertThat(attempts).hasValue(1);
    }

    @Test
    void retryAfterIsMinimumWhenLongerThanCalculatedBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));
        Mono<String> request = succeedsOnSecondAttempt(attempts, responseException(429, "3"));

        StepVerifier.withVirtualTime(() -> resilience.execute(request))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(3))
                .expectNext("ok")
                .verifyComplete();
    }

    @Test
    void calculatedBackoffWinsWhenLongerThanRetryAfter() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(3), Duration.ofSeconds(5));
        Mono<String> request = succeedsOnSecondAttempt(attempts, responseException(503, "1"));

        StepVerifier.withVirtualTime(() -> resilience.execute(request))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(3))
                .expectNext("ok")
                .verifyComplete();
    }

    @Test
    void supportsHttpDateRetryAfter() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));
        String retryAfter = ZonedDateTime.ofInstant(NOW.plusSeconds(4), ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        StepVerifier.withVirtualTime(() -> resilience.execute(
                        succeedsOnSecondAttempt(attempts, responseException(503, retryAfter))))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(4))
                .expectNext("ok")
                .verifyComplete();
    }

    @Test
    void rejectsRetryAfterBeyondIndependentMaximumWithoutRetryingEarly() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));

        StepVerifier.withVirtualTime(() -> resilience.execute(Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(responseException(429, "31"));
                })))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(FuelFinderIntegrationException.class);
                    assertThat(error).hasMessageContaining("exceeds configured maximum");
                })
                .verify();

        assertThat(attempts).hasValue(1);
    }

    @Test
    void invalidRetryAfterFallsBackToCalculatedBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(2), Duration.ofSeconds(5));

        StepVerifier.withVirtualTime(() -> resilience.execute(
                        succeedsOnSecondAttempt(attempts, responseException(429, "not-a-delay"))))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(2))
                .expectNext("ok")
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "Wed, 22 Jul 2026 12:00:00 GMT"
    })
    void zeroOrPastRetryAfterFallsBackToCalculatedBackoff(String retryAfter) {
        AtomicInteger attempts = new AtomicInteger();
        FuelFinderHttpResilience resilience = resilience(Duration.ofSeconds(1), Duration.ofSeconds(5));

        StepVerifier.withVirtualTime(() -> resilience.execute(
                        succeedsOnSecondAttempt(attempts, responseException(503, retryAfter))))
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("ok")
                .verifyComplete();
    }

    private static Mono<String> succeedsOnSecondAttempt(
            AtomicInteger attempts,
            RuntimeException firstFailure
    ) {
        return Mono.defer(() -> attempts.incrementAndGet() == 1
                ? Mono.error(firstFailure)
                : Mono.just("ok"));
    }

    private static FuelFinderHttpResilience resilience(Duration initialBackoff, Duration maxBackoff) {
        FuelFinderApiProperties properties = new FuelFinderApiProperties();
        properties.getHttp().getRetry().setMaxRetries(2);
        properties.getHttp().getRetry().setInitialBackoff(initialBackoff);
        properties.getHttp().getRetry().setMaxBackoff(maxBackoff);
        properties.getHttp().getRetry().setMaxRetryAfter(Duration.ofSeconds(30));
        properties.getHttp().getRetry().setJitter(0);
        return new FuelFinderHttpResilience(
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> 0.5
        );
    }

    private static WebClientResponseException responseException(int status, String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) {
            headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return WebClientResponseException.create(
                status,
                "test",
                headers,
                "{}".getBytes(),
                null
        );
    }

    private static WebClientRequestException requestException(Throwable cause) {
        return new WebClientRequestException(
                cause,
                HttpMethod.GET,
                URI.create("https://example.test/pfs"),
                HttpHeaders.EMPTY
        );
    }
}
