package uk.co.fuelfinder.ingestion.raw.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "fuelfinder.api")
public class FuelFinderApiProperties {

    @NotBlank
    private String baseUrl;

    @Valid
    @NotNull
    private OAuth oauth;

    @Valid
    @NotNull
    private Http http = new Http();

    @Getter
    @Setter
    @ToString
    public static class OAuth {

        @NotBlank
        private String clientId;

        @NotBlank
        @ToString.Exclude
        private String clientSecret;

        @NotBlank
        private String tokenPath;

        private String refreshPath;
    }

    @Getter
    @Setter
    public static class Http {

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(5);

        @NotNull
        private Duration responseTimeout = Duration.ofSeconds(20);

        @Valid
        @NotNull
        private Pool pool = new Pool();

        @Valid
        @NotNull
        private Retry retry = new Retry();

        @AssertTrue(message = "connect-timeout and response-timeout must be greater than zero")
        public boolean isTimeoutConfigurationValid() {
            return isPositive(connectTimeout) && isPositive(responseTimeout);
        }
    }

    @Getter
    @Setter
    public static class Pool {

        @Min(1)
        private int maxConnections = 20;

        @Min(1)
        private int pendingAcquireMaxCount = 40;

        @NotNull
        private Duration pendingAcquireTimeout = Duration.ofSeconds(5);

        @NotNull
        private Duration maxIdleTime = Duration.ofSeconds(30);

        @NotNull
        private Duration maxLifeTime = Duration.ofMinutes(5);

        @NotNull
        private Duration evictionInterval = Duration.ofSeconds(30);

        @AssertTrue(message = "pool durations must be greater than zero")
        public boolean isDurationConfigurationValid() {
            return isPositive(pendingAcquireTimeout)
                    && isPositive(maxIdleTime)
                    && isPositive(maxLifeTime)
                    && isPositive(evictionInterval);
        }
    }

    @Getter
    @Setter
    public static class Retry {

        @Min(0)
        private int maxRetries = 2;

        @NotNull
        private Duration initialBackoff = Duration.ofMillis(500);

        @NotNull
        private Duration maxBackoff = Duration.ofSeconds(5);

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double jitter = 0.5;

        @NotNull
        private Duration maxRetryAfter = Duration.ofSeconds(30);

        @AssertTrue(message = "max-backoff must be greater than or equal to initial-backoff")
        public boolean isBackoffRangeValid() {
            return isPositive(initialBackoff)
                    && isPositive(maxBackoff)
                    && isPositive(maxRetryAfter)
                    && !maxBackoff.minus(initialBackoff).isNegative();
        }
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
