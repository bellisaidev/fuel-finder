package uk.co.fuelfinder.ingestion.raw.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
}