package uk.co.fuelfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import uk.co.fuelfinder.ingestion.raw.auth.FuelFinderApiProperties;

@SpringBootApplication
@EnableConfigurationProperties(FuelFinderApiProperties.class)
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
