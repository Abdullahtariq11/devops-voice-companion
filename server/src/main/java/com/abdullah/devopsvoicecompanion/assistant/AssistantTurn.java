package com.abdullah.devopsvoicecompanion.assistant;

/** Previous conversational text, treated as context rather than routing instructions. */
public record AssistantTurn(String role, String text) {}
