package com.abdullah.devopsvoicecompanion.assistant;

import java.util.List;

/** Carries a question and bounded conversation context for AI tool selection. */
public record AssistantRequest(
    String message, String repository, Long runId, List<AssistantTurn> history) {
  public AssistantRequest(String message, String repository) {
    this(message, repository, null, List.of());
  }
}
