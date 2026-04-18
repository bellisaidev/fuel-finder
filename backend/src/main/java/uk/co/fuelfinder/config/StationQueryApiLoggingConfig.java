package uk.co.fuelfinder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.co.fuelfinder.api.ApiRequestLoggingInterceptor;

@Configuration
public class StationQueryApiLoggingConfig implements WebMvcConfigurer {

    @Bean
    public ApiRequestLoggingInterceptor apiRequestLoggingInterceptor() {
        return new ApiRequestLoggingInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRequestLoggingInterceptor())
                .addPathPatterns("/v1/stations/**");
    }
}
