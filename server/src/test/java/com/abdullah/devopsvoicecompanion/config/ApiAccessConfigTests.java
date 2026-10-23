package com.abdullah.devopsvoicecompanion.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/** Covers bearer protection and CORS without starting AWS clients. */
class ApiAccessConfigTests {
  @Test
  void requiresProductionTokenAndRejectsWildcardCors() {
    assertThrows(IllegalArgumentException.class,
        () -> new ApiAccessConfig().apiAccessFilter("", "production"));
    assertThrows(IllegalArgumentException.class, () -> new CorsConfig().corsFilter("*"));
  }

  @Test
  void rejectsMissingAndIncorrectTokensButAllowsCorrectToken() throws Exception {
    var filter = new ApiAccessConfig().apiAccessFilter("test-secret", "production").getFilter();
    for (String token : new String[] {"", "Bearer wrong", "Bearer test-secret-extra", "Basic test-secret"}) {
      var request = new MockHttpServletRequest("POST", "/api/assistant/route");
      request.addHeader("Authorization", token);
      var response = new MockHttpServletResponse();
      var chain = new MockFilterChain();
      filter.doFilter(request, response, chain);
      assertEquals(401, response.getStatus());
      assertNull(chain.getRequest());
    }
    var request = new MockHttpServletRequest("POST", "/api/assistant/route");
    request.addHeader("Authorization", "Bearer test-secret");
    var chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    assertNotNull(chain.getRequest());
  }

  @Test
  void allowsValidPreflightAndRejectsForeignOrigin() throws Exception {
    var cors = new CorsConfig().corsFilter("http://localhost:5173,https://example.vercel.app").getFilter();
    for (String endpoint : new String[] {"/mcp", "/api/assistant/route"}) {
      var request = new MockHttpServletRequest("OPTIONS", endpoint);
      request.addHeader("Origin", "https://example.vercel.app");
      request.addHeader("Access-Control-Request-Method", "POST");
      request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
      var response = new MockHttpServletResponse();
      cors.doFilter(request, response, new MockFilterChain());
      assertEquals("https://example.vercel.app", response.getHeader("Access-Control-Allow-Origin"));
    }
    var request = new MockHttpServletRequest("POST", "/mcp");
    request.addHeader("Origin", "https://untrusted.example");
    var response = new MockHttpServletResponse();
    cors.doFilter(request, response, new MockFilterChain());
    assertEquals(403, response.getStatus());
  }
}
