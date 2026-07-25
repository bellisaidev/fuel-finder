package uk.co.fuelfinder.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

@Configuration
public class FuelFinderWebClientConfig {

    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024; // 16 MB

    @Bean(destroyMethod = "dispose")
    public ConnectionProvider fuelFinderConnectionProvider(FuelFinderApiProperties properties) {
        FuelFinderApiProperties.Pool pool = properties.getHttp().getPool();
        return ConnectionProvider.builder("fuel-finder")
                .maxConnections(pool.getMaxConnections())
                .pendingAcquireMaxCount(pool.getPendingAcquireMaxCount())
                .pendingAcquireTimeout(pool.getPendingAcquireTimeout())
                .maxIdleTime(pool.getMaxIdleTime())
                .maxLifeTime(pool.getMaxLifeTime())
                .evictInBackground(pool.getEvictionInterval())
                .build();
    }

    @Bean
    public HttpClient fuelFinderHttpClient(
            @Qualifier("fuelFinderConnectionProvider") ConnectionProvider connectionProvider,
            FuelFinderApiProperties properties
    ) {
        return HttpClient.create(connectionProvider)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.getHttp().getConnectTimeout().toMillis())
                )
                .responseTimeout(properties.getHttp().getResponseTimeout());
    }

    @Bean
    public ClientHttpConnector fuelFinderClientHttpConnector(
            @Qualifier("fuelFinderHttpClient") HttpClient httpClient
    ) {
        return new ReactorClientHttpConnector(httpClient);
    }

    @Bean
    public ExchangeStrategies fuelFinderExchangeStrategies() {
        return ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    @Bean
    public WebClient fuelFinderAuthWebClient(
            WebClient.Builder builder,
            FuelFinderApiProperties properties,
            @Qualifier("fuelFinderClientHttpConnector") ClientHttpConnector connector,
            @Qualifier("fuelFinderExchangeStrategies") ExchangeStrategies exchangeStrategies
    ) {
        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(connector)
                .exchangeStrategies(exchangeStrategies)
                .build();
    }

    @Bean
    public WebClient fuelFinderApiWebClient(
            WebClient.Builder builder,
            FuelFinderApiProperties properties,
            @Qualifier("fuelFinderClientHttpConnector") ClientHttpConnector connector,
            @Qualifier("fuelFinderExchangeStrategies") ExchangeStrategies exchangeStrategies
    ) {
        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(connector)
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}
