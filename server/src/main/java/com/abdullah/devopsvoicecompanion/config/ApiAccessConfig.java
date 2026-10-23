package com.abdullah.devopsvoicecompanion.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protects AI and MCP endpoints with an optional shared token; production requires one. */
@Configuration
public class ApiAccessConfig {
  @Bean
  public FilterRegistrationBean<OncePerRequestFilter> apiAccessFilter(
      @Value("${app.auth.token:}") String token,
      @Value("${app.environment:development}") String environment) {
    if ("production".equalsIgnoreCase(environment) && token.isBlank()) {
      throw new IllegalArgumentException("APP_AUTH_TOKEN is required in production.");
    }
    var filter = new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
          FilterChain chain) throws ServletException, IOException {
        if (!"OPTIONS".equals(request.getMethod()) && !token.isBlank()) {
          String authorization = request.getHeader("Authorization");
          if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
              || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                  authorization.substring(7).getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"A valid MCP server bearer token is required.\"}");
            return;
          }
        }
        if (request.getContentLengthLong() > 65536) {
          response.sendError(413, "Request body is too large.");
          return;
        }
        chain.doFilter(request, response);
      }
    };
    var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
    registration.addUrlPatterns("/mcp", "/api/assistant/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
