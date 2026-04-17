package uk.co.fuelfinder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fuelFinderOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fuel Finder API")
                        .version("v1")
                        .description("REST API for querying nearby UK fuel stations and current fuel prices.")
                        .license(new License()
                                .name("Proprietary License (All Rights Reserved)")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server")
                ));
    }
}
