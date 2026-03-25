package uk.co.fuelfinder.ingestion.auth;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ToString
@ConfigurationProperties(prefix = "fuelfinder.api")
public class FuelFinderApiProperties {

    private String baseUrl;
    private OAuth oauth;

    @Getter
    @Setter
    @ToString
    public static class OAuth {

        private String clientId;

        @ToString.Exclude
        private String clientSecret;

        private String tokenPath;
        private String refreshPath;
    }
}