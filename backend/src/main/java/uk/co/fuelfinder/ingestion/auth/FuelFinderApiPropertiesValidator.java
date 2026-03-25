package uk.co.fuelfinder.ingestion.auth;

import org.springframework.stereotype.Component;

@Component
public class FuelFinderApiPropertiesValidator {

    private final FuelFinderApiProperties properties;

    public FuelFinderApiPropertiesValidator(FuelFinderApiProperties properties) {
        this.properties = properties;
    }

    public void validate() {
        if (isBlank(properties.getBaseUrl())) {
            throw new IllegalStateException("fuelfinder.api.base-url must be configured");
        }

        if (properties.getOauth() == null) {
            throw new IllegalStateException("fuelfinder.api.oauth must be configured");
        }

        if (isBlank(properties.getOauth().getClientId())) {
            throw new IllegalStateException("fuelfinder.api.oauth.client-id must be configured");
        }

        if (isBlank(properties.getOauth().getClientSecret())) {
            throw new IllegalStateException("fuelfinder.api.oauth.client-secret must be configured");
        }

        if (isBlank(properties.getOauth().getTokenPath())) {
            throw new IllegalStateException("fuelfinder.api.oauth.token-path must be configured");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}