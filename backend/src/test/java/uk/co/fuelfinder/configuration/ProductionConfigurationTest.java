package uk.co.fuelfinder.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationTest {

    private static final String CLIENT_ID = "FUEL_FINDER_CLIENT_ID=production-client";
    private static final String CLIENT_SECRET = "FUEL_FINDER_CLIENT_SECRET=production-secret";
    private static final String DATASOURCE_URL =
            "SPRING_DATASOURCE_URL=jdbc:postgresql://database.example.test:5432/fuelfinder";
    private static final String DATASOURCE_USERNAME = "SPRING_DATASOURCE_USERNAME=production-user";
    private static final String DATASOURCE_PASSWORD = "SPRING_DATASOURCE_PASSWORD=production-password";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(ProductionPropertiesConfiguration.class)
            .withPropertyValues("spring.profiles.active=prod");

    @Test
    void productionProfileBindsRequiredConfigurationAndAppliesHardenedPolicies() {
        contextRunner
                .withPropertyValues(
                        CLIENT_ID,
                        CLIENT_SECRET,
                        DATASOURCE_URL,
                        DATASOURCE_USERNAME,
                        DATASOURCE_PASSWORD)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getActiveProfiles()).contains("prod");

                    FuelFinderApiProperties apiProperties = context.getBean(FuelFinderApiProperties.class);
                    assertThat(apiProperties.getOauth().getClientId()).isEqualTo("production-client");
                    assertThat(apiProperties.getOauth().getClientSecret()).isEqualTo("production-secret");

                    DataSourceProperties datasourceProperties = context.getBean(DataSourceProperties.class);
                    assertThat(datasourceProperties.getUrl())
                            .isEqualTo("jdbc:postgresql://database.example.test:5432/fuelfinder");
                    assertThat(datasourceProperties.getUsername()).isEqualTo("production-user");
                    assertThat(datasourceProperties.getPassword()).isEqualTo("production-password");

                    assertThat(context.getEnvironment().getProperty(
                            "management.endpoints.web.exposure.include")).isEqualTo("health,info");
                    assertThat(context.getEnvironment().getProperty(
                            "management.endpoint.health.show-details")).isEqualTo("never");
                    assertThat(context.getEnvironment().getProperty(
                            "springdoc.api-docs.enabled", Boolean.class)).isFalse();
                    assertThat(context.getEnvironment().getProperty(
                            "springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
                });
    }

    @Test
    void rejectsMissingProductionClientId() {
        assertProductionConfigurationFailsWithout(CLIENT_ID);
    }

    @Test
    void rejectsBlankProductionClientId() {
        assertProductionConfigurationFailsWith("FUEL_FINDER_CLIENT_ID=   ");
    }

    @Test
    void rejectsMissingProductionClientSecret() {
        assertProductionConfigurationFailsWithout(CLIENT_SECRET);
    }

    @Test
    void rejectsBlankProductionClientSecret() {
        assertProductionConfigurationFailsWith("FUEL_FINDER_CLIENT_SECRET=   ");
    }

    @Test
    void requiresDatasourceUrlPlaceholderToResolve() {
        assertDatasourcePlaceholderIsRequired(DATASOURCE_URL, "spring.datasource.url");
    }

    @Test
    void requiresDatasourceUsernamePlaceholderToResolve() {
        assertDatasourcePlaceholderIsRequired(DATASOURCE_USERNAME, "spring.datasource.username");
    }

    @Test
    void requiresDatasourcePasswordPlaceholderToResolve() {
        assertDatasourcePlaceholderIsRequired(DATASOURCE_PASSWORD, "spring.datasource.password");
    }

    private void assertDatasourcePlaceholderIsRequired(String omittedProperty, String datasourceProperty) {
        contextRunner
                .withPropertyValues(requiredPropertiesExcept(omittedProperty))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> context.getEnvironment().getRequiredProperty(datasourceProperty))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining(omittedProperty.substring(0, omittedProperty.indexOf('=')));
                });
    }

    private void assertProductionConfigurationFailsWithout(String omittedProperty) {
        contextRunner
                .withPropertyValues(requiredPropertiesExcept(omittedProperty))
                .run(context -> assertOauthValidationFailure(context.getStartupFailure()));
    }

    private void assertProductionConfigurationFailsWith(String replacementProperty) {
        String propertyName = replacementProperty.substring(0, replacementProperty.indexOf('='));

        contextRunner
                .withPropertyValues(Stream.concat(
                                Arrays.stream(requiredProperties())
                                        .filter(property -> !property.startsWith(propertyName + "=")),
                                Stream.of(replacementProperty))
                        .toArray(String[]::new))
                .run(context -> assertOauthValidationFailure(context.getStartupFailure()));
    }

    private String[] requiredPropertiesExcept(String omittedProperty) {
        return Arrays.stream(requiredProperties())
                .filter(property -> !property.equals(omittedProperty))
                .toArray(String[]::new);
    }

    private void assertOauthValidationFailure(Throwable failure) {
        assertThat(failure).isNotNull();
        assertThat(Stream.iterate(failure, Objects::nonNull, Throwable::getCause))
                .anyMatch(BindValidationException.class::isInstance);
    }

    private String[] requiredProperties() {
        return new String[]{
                CLIENT_ID,
                CLIENT_SECRET,
                DATASOURCE_URL,
                DATASOURCE_USERNAME,
                DATASOURCE_PASSWORD
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({FuelFinderApiProperties.class, DataSourceProperties.class})
    static class ProductionPropertiesConfiguration {
    }
}
