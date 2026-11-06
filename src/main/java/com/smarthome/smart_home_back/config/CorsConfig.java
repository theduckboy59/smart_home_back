package com.smarthome.smart_home_back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:4200",
                                "http://127.0.0.1:4200",
                                "https://thehome.aeropuertolosprimos.me"
                        )
                        .allowedMethods(
                                "GET", "POST", "PUT", "DELETE", "OPTIONS"
                        )
                        .allowedHeaders("*");
            }
        };
    }
}
