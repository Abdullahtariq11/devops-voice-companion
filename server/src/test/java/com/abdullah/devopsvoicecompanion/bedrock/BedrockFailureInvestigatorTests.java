package com.abdullah.devopsvoicecompanion.bedrock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedrockFailureInvestigatorTests {
  private BedrockRuntimeClient client;
  private BedrockFailureInvestigator investigator;
  private ConverseRequest request;

  @BeforeEach
  void setUp() {
    client = mock(BedrockRuntimeClient.class);
    investigator = new BedrockFailureInvestigator(client);
  }

  @Test
  void usesOneForcedStructuredCallAndSeparatesHypotheses() {
    respond(report("Mismatch in PaymentServiceTest."));
    String answer = investigator.investigate("Expected 100, actual 90");
    assertTrue(answer.contains("Observed: Mismatch in PaymentServiceTest."));
    assertTrue(answer.contains("Possible cause (not confirmed):"));
    assertTrue(answer.contains("Next check:"));
    assertTrue(answer.contains("Limitations:"));
    assertNotNull(request.toolConfig().toolChoice().any());
    assertEquals(700, request.inferenceConfig().maxTokens());
    assertTrue(request.system().getFirst().text().contains("untrusted data"));
    assertTrue(request.system().getFirst().text().contains("simulate"));
    verify(client, times(1)).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"<thinking>private plan", "&lt;analysis>private", "```reasoning", " ", "<think>hidden"})
  void rejectsInternalMarkupAndEmptyFields(String text) {
    respond(report(text));
    assertThrows(IllegalStateException.class, () -> investigator.investigate("Evidence"));
  }

  @Test
  void rejectsMissingAndExtraFields() {
    var fields = new HashMap<>(report("Observed"));
    fields.put("extra", Document.fromString("unexpected"));
    respond(fields);
    assertThrows(IllegalStateException.class, () -> investigator.investigate("Evidence"));
    fields.remove("extra");
    fields.remove("limitations");
    respond(fields);
    assertThrows(IllegalStateException.class, () -> investigator.investigate("Evidence"));
  }

  @Test
  void rejectsRawModelTextRatherThanDisplayingIt() {
    when(client.converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any()))
        .thenReturn(ConverseResponse.builder().stopReason(StopReason.END_TURN)
            .output(output -> output.message(message -> message.content(ContentBlock.fromText("<thinking>private")))).build());
    assertThrows(IllegalStateException.class, () -> investigator.investigate("Evidence"));
  }

  @Test
  void rejectsOversizedInputWithoutAws() {
    assertThrows(IllegalArgumentException.class, () -> investigator.investigate("x".repeat(30001)));
    assertThrows(IllegalArgumentException.class, () -> investigator.investigate(null));
    verifyNoInteractions(client);
  }

  @Test
  void closesClient() {
    investigator.close();
    verify(client).close();
  }

  private Map<String, Document> report(String observed) {
    return Map.of("observed", Document.fromString(observed), "likelyCause", Document.fromString("Undetermined."),
        "nextCheck", Document.fromString("Inspect the referenced test."),
        "limitations", Document.fromString("Only head-commit excerpts were available."));
  }

  private void respond(Map<String, Document> fields) {
    doAnswer(invocation -> {
          Consumer<ConverseRequest.Builder> customize = invocation.getArgument(0);
          var builder = ConverseRequest.builder();
          customize.accept(builder);
          request = builder.build();
          return ConverseResponse.builder().stopReason(StopReason.TOOL_USE)
              .output(output -> output.message(message -> message.content(
                  ContentBlock.fromText("<thinking>ignored routing text"),
                  ContentBlock.fromToolUse(ToolUseBlock.builder().name("report_investigation")
                      .toolUseId("report").input(Document.fromMap(fields)).build())))).build();
        }).when(client).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
  }
}
