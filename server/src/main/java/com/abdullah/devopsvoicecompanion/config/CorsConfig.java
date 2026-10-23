package com.abdullah.devopsvoicecompanion.config;

import java.util.List;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

  @Bean
  public FilterRegistrationBean<CorsFilter> corsFilter(
      @Value("${app.cors.allowed-origins:http://localhost:5173}") String origins) {
    CorsConfiguration configuration = new CorsConfiguration();

    var allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim)
        .filter(origin -> !origin.isEmpty()).toList();
    if (allowedOrigins.isEmpty() || allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
      throw new IllegalArgumentException("Configure explicit frontend origins; wildcards are not permitted.");
    }
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of(
            "Accept",
            "Content-Type",
            "Authorization",
            "MCP-Protocol-Version",
            "Mcp-Session-Id",
            "Last-Event-ID"
    ));
    configuration.setExposedHeaders(List.of("Mcp-Session-Id"));
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/mcp", configuration);
    source.registerCorsConfiguration("/api/assistant/route", configuration);

    var registration = new FilterRegistrationBean<>(new CorsFilter(source));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
