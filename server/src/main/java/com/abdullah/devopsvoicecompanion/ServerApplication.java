package com.abdullah.devopsvoicecompanion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the Spring Boot application and its Streamable HTTP MCP server. */
@SpringBootApplication
public class ServerApplication {

  /**
   * Boots the server using Spring's application configuration.
   *
   * @param args command-line arguments passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(ServerApplication.class, args);
  }
}
