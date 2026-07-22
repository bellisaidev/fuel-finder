package uk.co.fuelfinder.ingestion.raw.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FuelFinderApiPropertiesTest {

    private static final String BASE_URL = "fuelfinder.api.base-url=https://example.test/api";
    private static final String TOKEN_PATH = "fuelfinder.api.oauth.token-path=/oauth/token";
    private static final String CLIENT_ID = "fuelfinder.api.oauth.client-id=test-client";
    private static final String CLIENT_SECRET = "fuelfinder.api.oauth.client-secret=test-secret";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsValidApiConfiguration() {
        contextRunner
                .withPropertyValues(BASE_URL, TOKEN_PATH, CLIENT_ID, CLIENT_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    FuelFinderApiProperties properties = context.getBean(FuelFinderApiProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://example.test/api");
                    assertThat(properties.getOauth().getTokenPath()).isEqualTo("/oauth/token");
                    assertThat(properties.getOauth().getClientId()).isEqualTo("test-client");
                    assertThat(properties.getOauth().getClientSecret()).isEqualTo("test-secret");
                });
    }

    @Test
    void rejectsMissingClientIdDuringConfigurationBinding() {
        contextRunner
                .withPropertyValues(BASE_URL, TOKEN_PATH, CLIENT_SECRET)
                .run(context -> assertValidationFailure(context.getStartupFailure(), "clientId", "client-id"));
    }

    @Test
    void rejectsBlankClientIdDuringConfigurationBinding() {
        contextRunner
                .withPropertyValues(BASE_URL, TOKEN_PATH, "fuelfinder.api.oauth.client-id=   ", CLIENT_SECRET)
                .run(context -> assertValidationFailure(context.getStartupFailure(), "clientId", "client-id"));
    }

    @Test
    void rejectsMissingClientSecretDuringConfigurationBinding() {
        contextRunner
                .withPropertyValues(BASE_URL, TOKEN_PATH, CLIENT_ID)
                .run(context -> assertValidationFailure(context.getStartupFailure(), "clientSecret", "client-secret"));
    }

    @Test
    void rejectsBlankClientSecretDuringConfigurationBinding() {
        contextRunner
                .withPropertyValues(BASE_URL, TOKEN_PATH, CLIENT_ID, "fuelfinder.api.oauth.client-secret=   ")
                .run(context -> assertValidationFailure(context.getStartupFailure(), "clientSecret", "client-secret"));
    }

    private static void assertValidationFailure(Throwable failure, String javaField, String propertyField) {
        assertThat(failure).isNotNull();
        assertThat(causes(failure)).anyMatch(ConfigurationPropertiesBindException.class::isInstance);
        assertThat(causes(failure)).anyMatch(BindValidationException.class::isInstance);

        String failureDetails = causes(failure)
                .map(Throwable::getMessage)
                .filter(message -> message != null)
                .map(message -> message.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + "\n" + right);

        assertThat(failureDetails)
                .containsAnyOf(javaField.toLowerCase(Locale.ROOT), propertyField)
                .doesNotContain("oauthtokenclient");
    }

    private static Stream<Throwable> causes(Throwable failure) {
        return Stream.iterate(failure, Objects::nonNull, Throwable::getCause);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FuelFinderApiProperties.class)
    static class PropertiesConfiguration {
    }
}
