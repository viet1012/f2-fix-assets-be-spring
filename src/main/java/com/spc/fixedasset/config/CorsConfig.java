package com.spc.fixedasset.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** Exact origins (comma-separated) from env CORS_ALLOWED_ORIGINS; falls back to the existing property. "*" is ignored. */
    @Value("${CORS_ALLOWED_ORIGINS:${fixed-asset.cors.allowed-origins:http://localhost:5173}}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.contains("*"))
                .toArray(String[]::new);
        if (origins.length == 0) return; // same origin only (e.g. through the Vite proxy)

        // Session cookie must travel cross-origin: credentials on, so only explicit origins are allowed.
        registry.addMapping("/api/**")
        .allowedOrigins(origins)
        .allowedMethods("GET", "POST", "OPTIONS")
        .allowedHeaders("Content-Type")
        .allowCredentials(true);
    }
}
