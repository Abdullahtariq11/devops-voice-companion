package com.abdullah.devopsvoicecompanion.assistant;

import java.util.List;

public record AssistantResponse(String message, List<AssistantToolCall> toolCalls) {}
