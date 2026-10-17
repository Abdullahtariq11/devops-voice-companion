package com.abdullah.devopsvoicecompanion.bedrock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Verifies a live Bedrock request when explicitly enabled. */
@EnabledIfEnvironmentVariable(named = "RUN_BEDROCK_TESTS", matches = "true")
class BedrockSummarizerTests {

  @Test
  void summarizesSampleFailure() {
    var summarizer = new BedrockSummarizer();

    String summary = summarizer.summarize(
        "demo/payments",
        """
        Job: Build & Test (failure)
        Failed step: Run tests
        PaymentServiceTest failed.
        Expected total: 100. Actual total: 90.
        """);

    assertNotNull(summary);
    assertFalse(summary.isBlank());

    System.out.println("Bedrock summary: " + summary);
  }
}
