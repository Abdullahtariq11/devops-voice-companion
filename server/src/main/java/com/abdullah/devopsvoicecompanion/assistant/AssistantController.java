package com.abdullah.devopsvoicecompanion.assistant;

import java.util.Map;
import java.util.concurrent.Semaphore;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;

/** Offers bounded AI planning while leaving actual execution on the MCP connection. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
  private final BedrockToolRouter router;
  private final Semaphore capacity = new Semaphore(4);

  public AssistantController(BedrockToolRouter router) {
    this.router = router;
  }

  @PostMapping(value = "/route", consumes = "application/json", produces = "application/json")
  public AssistantResponse route(@RequestBody AssistantRequest request) {
    if (!capacity.tryAcquire()) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Assistant is busy. Try again shortly.");
    }
    try {
      return router.route(request);
    } finally {
      capacity.release();
    }
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
  }

  @ExceptionHandler(SdkException.class)
  public ResponseEntity<Map<String, String>> serviceUnavailable() {
    return ResponseEntity.status(503).body(Map.of("message", "The AI service is unavailable. Check backend AWS access or try again shortly."));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> invalidDecision() {
    return ResponseEntity.status(502).body(Map.of("message", "The AI returned an invalid tool plan. Please rephrase your question."));
  }
}
