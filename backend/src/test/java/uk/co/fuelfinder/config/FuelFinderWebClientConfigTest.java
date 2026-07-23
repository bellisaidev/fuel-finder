package uk.co.fuelfinder.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FuelFinderWebClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class, FuelFinderWebClientConfig.class)
            .withPropertyValues(
                    "fuelfinder.api.base-url=https://example.test/api",
                    "fuelfinder.api.oauth.token-path=/oauth/token",
                    "fuelfinder.api.oauth.client-id=client",
                    "fuelfinder.api.oauth.client-secret=secret");

    @Test
    void createsDistinctClientsUsingOneSharedConnectorAndPoolGraph() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ConnectionProvider.class);
            assertThat(context).hasSingleBean(HttpClient.class);
            assertThat(context).hasSingleBean(ClientHttpConnector.class);

            WebClient authClient = context.getBean("fuelFinderAuthWebClient", WebClient.class);
            WebClient apiClient = context.getBean("fuelFinderApiWebClient", WebClient.class);
            assertThat(authClient).isNotSameAs(apiClient);
        });
    }

    @Test
    void enforcesConfiguredResponseTimeoutThroughObservableBehavior() {
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) ->
                        response.sendString(Mono.just("ok").delayElement(Duration.ofMillis(300))))
                .bindNow();
        ConnectionProvider provider = null;
        try {
            FuelFinderApiProperties properties = properties(server.port());
            FuelFinderWebClientConfig config = new FuelFinderWebClientConfig();
            provider = config.fuelFinderConnectionProvider(properties);
            HttpClient httpClient = config.fuelFinderHttpClient(provider, properties);
            WebClient client = WebClient.builder()
                    .clientConnector(config.fuelFinderClientHttpConnector(httpClient))
                    .build();

            assertThatThrownBy(() -> client.get()
                    .uri("http://127.0.0.1:" + server.port() + "/slow")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
                    .isInstanceOf(WebClientRequestException.class);
        } finally {
            if (provider != null) {
                provider.dispose();
            }
            server.disposeNow();
        }
    }

    private static FuelFinderApiProperties properties(int port) {
        FuelFinderApiProperties properties = new FuelFinderApiProperties();
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.getHttp().setConnectTimeout(Duration.ofSeconds(1));
        properties.getHttp().setResponseTimeout(Duration.ofMillis(50));
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FuelFinderApiProperties.class)
    static class TestConfiguration {

        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }
    }
}
