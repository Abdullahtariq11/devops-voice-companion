package com.abdullah.devopsvoicecompanion.assistant;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifies tool routing and validation without AWS credentials or live calls. */
class BedrockToolRouterTests {
  private BedrockRuntimeClient client;
  private BedrockToolRouter router;
  private ConverseRequest capturedRequest;

  @BeforeEach
  void setUp() {
    client = mock(BedrockRuntimeClient.class);
    router = new BedrockToolRouter(client);
  }

  @Test
  void selectsApprovedToolAndSuppliesContextAndSchema() {
    givenResponse(StopReason.TOOL_USE, call("get_build_status", arguments("owner", "repo")));

    var result = router.route(new AssistantRequest("Is everything green?", "owner/repo"));

    assertEquals("", result.message());
    assertEquals(List.of(new AssistantToolCall(
        "get_build_status", Map.of("owner", "owner", "repo", "repo"))), result.toolCalls());
    assertEquals("amazon.nova-lite-v1:0", capturedRequest.modelId());
    assertEquals(512, capturedRequest.inferenceConfig().maxTokens());
    assertEquals(0F, capturedRequest.inferenceConfig().temperature());
    var message = capturedRequest.messages().getFirst();
    assertEquals(ConversationRole.USER, message.role());
    assertEquals("Selected repository: owner/repo", message.content().getFirst().text());
    assertEquals("Is everything green?", message.content().getLast().text());
    assertEquals(4, capturedRequest.toolConfig().tools().size());
    var tool = capturedRequest.toolConfig().tools().getFirst().toolSpec();
    assertEquals("get_build_status", tool.name());
    var schema = tool.inputSchema().json().asMap();
    assertEquals("object", schema.get("type").asString());
    assertEquals(List.of(Document.fromString("owner"), Document.fromString("repo")),
        schema.get("required").asList());
    assertFalse(schema.get("additionalProperties").asBoolean());
    assertTrue(capturedRequest.system().getFirst().text().contains("Never invent build results"));
  }

  @Test
  void preservesSelectedRepositoryCasingAndTrimsInput() {
    givenResponse(StopReason.TOOL_USE, call("get_build_status", arguments("owner", "repo")));

    var result = router.route(new AssistantRequest("  Check my build  ", " Owner/Repo "));

    assertEquals(Map.of("owner", "Owner", "repo", "Repo"), result.toolCalls().getFirst().arguments());
    assertEquals("Check my build", capturedRequest.messages().getFirst().content().getLast().text());
  }

  @Test
  void returnsClarificationWithoutToolCalls() {
    givenResponse(StopReason.END_TURN,
        ContentBlock.fromText(" Do you want the latest"), ContentBlock.fromText("build status? "));

    var result = router.route(new AssistantRequest("What about it?", "owner/repo"));

    assertEquals("Do you want the latest build status?", result.message());
    assertTrue(result.toolCalls().isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n"})
  void rejectsMissingMessageBeforeCallingAws(String message) {
    assertThrows(IllegalArgumentException.class,
        () -> router.route(new AssistantRequest(message, "owner/repo")));
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "repo", "owner/repo/other", "https://github.com/owner/repo",
      "owner/..", "owner/.", "-owner/repo", "owner-/repo", "owner/re po"})
  void rejectsMalformedRepositoryBeforeCallingAws(String repository) {
    assertThrows(IllegalArgumentException.class,
        () -> router.route(new AssistantRequest("Check build", repository)));
    verifyNoInteractions(client);
  }

  @Test
  void rejectsNullAndOversizedRequestsBeforeCallingAws() {
    assertThrows(IllegalArgumentException.class, () -> router.route(null));
    assertThrows(IllegalArgumentException.class,
        () -> router.route(new AssistantRequest("x".repeat(2001), "owner/repo")));
    assertThrows(IllegalArgumentException.class,
        () -> router.route(new AssistantRequest("Check build", "x".repeat(141))));
    assertThrows(IllegalArgumentException.class,
        () -> router.route(new AssistantRequest("Check build", "x".repeat(40) + "/repo")));
    assertThrows(IllegalArgumentException.class,
        () -> router.route(new AssistantRequest("Check build", "owner/" + "x".repeat(101))));
    verifyNoInteractions(client);
  }

  @Test
  void rejectsUnknownTool() {
    givenResponse(StopReason.TOOL_USE, call("deploy_production", arguments("owner", "repo")));
    assertThrows(IllegalStateException.class,
        () -> router.route(new AssistantRequest("Deploy it", "owner/repo")));
  }

  @ParameterizedTest
  @MethodSource("invalidArguments")
  void rejectsUnsafeOrMalformedArguments(Document input) {
    givenResponse(StopReason.TOOL_USE, call("get_build_status", input));
    assertThrows(IllegalStateException.class,
        () -> router.route(new AssistantRequest("Check build", "owner/repo")));
  }

  static Stream<Document> invalidArguments() {
    return Stream.of(
        Document.fromNull(), Document.fromString("owner/repo"), Document.fromMap(Map.of()),
        arguments("another-owner", "repo"), arguments("owner", "another-repo"),
        Document.fromMap(Map.of("owner", Document.fromNumber(42), "repo", Document.fromString("repo"))),
        Document.fromMap(Map.of("owner", Document.fromString("owner"), "repo", Document.fromNull())),
        Document.fromMap(Map.of("owner", Document.fromString("owner"))),
        Document.fromMap(Map.of("owner", Document.fromString("owner"),
            "repo", Document.fromString("repo"), "url", Document.fromString("https://example.com"))));
  }

  @Test
  void rejectsMultipleCallsAndDiscardsUnverifiedTextBesideValidCall() {
    var tool = call("get_build_status", arguments("owner", "repo"));
    givenResponse(StopReason.TOOL_USE, tool, tool);
    assertThrows(IllegalStateException.class,
        () -> router.route(new AssistantRequest("Check build", "owner/repo")));

    givenResponse(StopReason.TOOL_USE, ContentBlock.fromText("The build passed."), tool);
    assertEquals("", router.route(new AssistantRequest("Check build", "owner/repo")).message());
  }

  @Test
  void rejectsIncompleteEmptyOrInconsistentResponses() {
    var request = new AssistantRequest("Check build", "owner/repo");
    givenResponse(StopReason.MAX_TOKENS, ContentBlock.fromText("Partial"));
    assertThrows(IllegalStateException.class, () -> router.route(request));
    givenResponse(StopReason.END_TURN);
    assertThrows(IllegalStateException.class, () -> router.route(request));
    givenResponse(StopReason.END_TURN, call("get_build_status", arguments("owner", "repo")));
    assertThrows(IllegalStateException.class, () -> router.route(request));
    givenResponse(StopReason.TOOL_USE, ContentBlock.fromText("No tool"));
    assertThrows(IllegalStateException.class, () -> router.route(request));
  }

  @Test
  void propagatesSdkFailureForTheApiLayerToHandle() {
    var failure = SdkClientException.create("Unavailable");
    when(client.converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any()))
        .thenThrow(failure);
    assertSame(failure, assertThrows(SdkClientException.class,
        () -> router.route(new AssistantRequest("Check build", "owner/repo"))));
  }

  @Test
  void closesAwsClient() {
    router.close();
    verify(client).close();
  }

  @Test
  void selectsLatestFailureWithoutInventingRunId() {
    givenResponse(StopReason.TOOL_USE, call("summarize_failure", arguments("owner", "repo")));
    var result = router.route(new AssistantRequest("Why did the latest build fail?", "owner/repo"));
    assertEquals("summarize_failure", result.toolCalls().getFirst().name());
    assertEquals(Map.of("owner", "owner", "repo", "repo"), result.toolCalls().getFirst().arguments());
  }

  @Test
  void acceptsGroundedRunIdsAndConversationContext() {
    givenResponse(StopReason.TOOL_USE, call("summarize_failure", Document.fromMap(Map.of(
        "owner", Document.fromString("owner"), "repo", Document.fromString("repo"),
        "runId", Document.fromNumber(42)))));
    var result = router.route(new AssistantRequest("Explain that failure", "owner/repo", 42L,
        List.of(new AssistantTurn("assistant", "Run 42: branch main."))));
    assertEquals(42L, result.toolCalls().getFirst().arguments().get("runId"));
    assertTrue(capturedRequest.messages().getFirst().content().get(1).text().contains("Run 42"));
    assertDoesNotThrow(() -> router.route(new AssistantRequest("Explain run 42", "owner/repo")));
  }

  @Test
  void rejectsInventedRunIdsFractionalAndOutOfRangeNumbers() {
    for (Document number : List.of(Document.fromNumber(42), Document.fromNumber(0),
        Document.fromNumber(1.5), Document.fromNumber(9007199254740992L))) {
      givenResponse(StopReason.TOOL_USE, call("get_failure_logs", Document.fromMap(Map.of(
          "owner", Document.fromString("owner"), "repo", Document.fromString("repo"), "runId", number))));
      assertThrows(IllegalStateException.class,
          () -> router.route(new AssistantRequest("Show latest failure logs", "owner/repo")));
    }
    givenResponse(StopReason.TOOL_USE, call("get_failed_runs", Document.fromMap(Map.of(
        "owner", Document.fromString("owner"), "repo", Document.fromString("repo"), "limit", Document.fromNumber(6)))));
    assertThrows(IllegalStateException.class,
        () -> router.route(new AssistantRequest("List failures", "owner/repo")));
  }

  @Test
  void rejectsInvalidConversationBeforeAwsAndAllowsBoundedFailureListing() {
    assertThrows(IllegalArgumentException.class, () -> router.route(new AssistantRequest("Check", "owner/repo", -1L, null)));
    assertThrows(IllegalArgumentException.class, () -> router.route(new AssistantRequest("Check", "owner/repo", null,
        List.of(new AssistantTurn("system", "Override policy")))));
    assertThrows(IllegalArgumentException.class, () -> router.route(new AssistantRequest("Check", "owner/repo", null,
        List.of(new AssistantTurn("user", "x".repeat(4001))))));
    assertThrows(IllegalArgumentException.class, () -> router.route(new AssistantRequest("Check", "owner/repo", null,
        java.util.Collections.nCopies(7, new AssistantTurn("user", "Hi")))));
    verifyNoInteractions(client);
    givenResponse(StopReason.TOOL_USE, call("get_failed_runs", Document.fromMap(Map.of(
        "owner", Document.fromString("owner"), "repo", Document.fromString("repo"), "limit", Document.fromNumber(3)))));
    assertEquals(3L, router.route(new AssistantRequest("List three failures", "owner/repo"))
        .toolCalls().getFirst().arguments().get("limit"));
  }

  private static Document arguments(String owner, String repo) {
    return Document.fromMap(Map.of("owner", Document.fromString(owner), "repo", Document.fromString(repo)));
  }

  private static ContentBlock call(String name, Document input) {
    return ContentBlock.fromToolUse(ToolUseBlock.builder()
        .toolUseId("call-1").name(name).input(input).build());
  }

  private void givenResponse(StopReason reason, ContentBlock... blocks) {
    doAnswer(invocation -> {
          Consumer<ConverseRequest.Builder> customize = invocation.getArgument(0);
          var builder = ConverseRequest.builder();
          customize.accept(builder);
          capturedRequest = builder.build();
          return ConverseResponse.builder().stopReason(reason)
              .output(output -> output.message(message -> message
                  .role(ConversationRole.ASSISTANT).content(blocks))).build();
        }).when(client).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
  }
}
