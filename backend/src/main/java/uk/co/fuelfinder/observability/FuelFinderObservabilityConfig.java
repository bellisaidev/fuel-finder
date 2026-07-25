package uk.co.fuelfinder.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class FuelFinderObservabilityConfig {

    @Bean
    public Clock fuelFinderObservabilityClock() {
        return Clock.systemUTC();
    }
}
