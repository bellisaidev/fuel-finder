package uk.co.fuelfinder.ingestion.raw.http;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import reactor.util.retry.Retry;
import uk.co.fuelfinder.ingestion.exception.FuelFinderIntegrationException;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.DoubleSupplier;

@Component
public class FuelFinderHttpResilience {

    private final FuelFinderApiProperties.Retry properties;
    private final Clock clock;
    private final DoubleSupplier jitterSource;

    public FuelFinderHttpResilience(FuelFinderApiProperties properties) {
        this(properties, Clock.systemUTC(), () -> ThreadLocalRandom.current().nextDouble());
    }

    FuelFinderHttpResilience(
            FuelFinderApiProperties properties,
            Clock clock,
            DoubleSupplier jitterSource
    ) {
        this.properties = properties.getHttp().getRetry();
        this.clock = clock;
        this.jitterSource = jitterSource;
    }

    public <T> Mono<T> execute(Mono<T> request) {
        return request.retryWhen(Retry.from(retrySignals ->
                retrySignals.concatMap(signal -> retrySignal(signal.totalRetries(), signal.failure()))
        ));
    }

    public boolean isRetryableTransportFailure(Throwable failure) {
        if (hasCause(failure, SSLException.class) || hasCause(failure, CertificateException.class)) {
            return false;
        }

        if (!(failure instanceof WebClientRequestException)) {
            return false;
        }

        return hasCause(failure, ConnectException.class)
                || hasCause(failure, UnknownHostException.class)
                || hasCause(failure, SocketTimeoutException.class)
                || hasCause(failure, TimeoutException.class)
                || hasCause(failure, io.netty.handler.timeout.TimeoutException.class)
                || hasCause(failure, PrematureCloseException.class)
                || hasCause(failure, IOException.class);
    }

    private Mono<Long> retrySignal(long retriesSoFar, Throwable failure) {
        if (!isRetryable(failure) || retriesSoFar >= properties.getMaxRetries()) {
            return Mono.error(failure);
        }

        Duration backoff = jitteredBackoff(retriesSoFar);
        RetryAfterDecision retryAfter = retryAfter(failure);
        if (retryAfter.exceedsMaximum()) {
            return Mono.error(new FuelFinderIntegrationException(
                    "Fuel Finder Retry-After delay " + retryAfter.delay()
                            + " exceeds configured maximum " + properties.getMaxRetryAfter(),
                    failure
            ));
        }

        Duration delay = retryAfter.delay() == null || backoff.compareTo(retryAfter.delay()) >= 0
                ? backoff
                : retryAfter.delay();
        return Mono.delay(delay);
    }

    private boolean isRetryable(Throwable failure) {
        if (isRetryableTransportFailure(failure)) {
            return true;
        }
        if (failure instanceof WebClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == HttpStatus.REQUEST_TIMEOUT.value()
                    || status == HttpStatus.TOO_MANY_REQUESTS.value()
                    || status == HttpStatus.INTERNAL_SERVER_ERROR.value()
                    || status == HttpStatus.BAD_GATEWAY.value()
                    || status == HttpStatus.SERVICE_UNAVAILABLE.value()
                    || status == HttpStatus.GATEWAY_TIMEOUT.value();
        }
        return false;
    }

    private Duration jitteredBackoff(long retriesSoFar) {
        Duration exponential = exponentialBackoff(retriesSoFar);
        double jitter = properties.getJitter();
        if (jitter == 0) {
            return exponential;
        }

        double random = Math.max(0, Math.min(1, jitterSource.getAsDouble()));
        double multiplier = (1 - jitter) + (2 * jitter * random);
        long jitteredMillis = Math.max(1, Math.round(exponential.toMillis() * multiplier));
        Duration jittered = Duration.ofMillis(jitteredMillis);
        return min(jittered, properties.getMaxBackoff());
    }

    private Duration exponentialBackoff(long retriesSoFar) {
        long multiplier = 1L << Math.min(retriesSoFar, 30);
        try {
            return min(properties.getInitialBackoff().multipliedBy(multiplier), properties.getMaxBackoff());
        } catch (ArithmeticException ignored) {
            return properties.getMaxBackoff();
        }
    }

    private RetryAfterDecision retryAfter(Throwable failure) {
        if (!(failure instanceof WebClientResponseException responseException)) {
            return RetryAfterDecision.absent();
        }

        int status = responseException.getStatusCode().value();
        if (status != HttpStatus.TOO_MANY_REQUESTS.value()
                && status != HttpStatus.SERVICE_UNAVAILABLE.value()) {
            return RetryAfterDecision.absent();
        }

        Duration delay = parseRetryAfter(responseException.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        if (delay == null || delay.isZero() || delay.isNegative()) {
            return RetryAfterDecision.absent();
        }
        return new RetryAfterDecision(delay, delay.compareTo(properties.getMaxRetryAfter()) > 0);
    }

    private Duration parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // Try the RFC 1123 HTTP-date form below.
        }

        try {
            Instant retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Duration.between(clock.instant(), retryAt);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private record RetryAfterDecision(Duration delay, boolean exceedsMaximum) {

        private static RetryAfterDecision absent() {
            return new RetryAfterDecision(null, false);
        }
    }
}
