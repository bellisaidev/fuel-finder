package uk.co.fuelfinder.ingestion.raw.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;
import uk.co.fuelfinder.ingestion.raw.auth.OAuthTokenClient;

import static org.assertj.core.api.Assertions.assertThat;

class FuelFinderHttpComponentWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "fuelfinder.api.base-url=https://example.test/api",
                    "fuelfinder.api.oauth.token-path=/oauth/token",
                    "fuelfinder.api.oauth.client-id=client",
                    "fuelfinder.api.oauth.client-secret=secret");

    @Test
    void createsResilienceAndOAuthClientThroughSpringConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FuelFinderHttpResilience.class);
            assertThat(context).hasSingleBean(FuelFinderHttpExceptionMapper.class);
            assertThat(context).hasSingleBean(OAuthTokenClient.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FuelFinderApiProperties.class)
    @Import({
            FuelFinderHttpResilience.class,
            FuelFinderHttpExceptionMapper.class,
            OAuthTokenClient.class
    })
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        @Qualifier("fuelFinderAuthWebClient")
        WebClient fuelFinderAuthWebClient() {
            return WebClient.builder().build();
        }
    }
}
