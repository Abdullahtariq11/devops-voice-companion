package com.abdullah.devopsvoicecompanion.assistant;

import java.util.Map;

public record AssistantToolCall(String name, Map<String, Object> arguments) {}
