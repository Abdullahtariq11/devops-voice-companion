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
    assertEquals(7, capturedRequest.toolConfig().tools().size());
    assertNotNull(capturedRequest.toolConfig().toolChoice().any());
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

    assertTrue(result.message().startsWith("Would you like the latest build status"));
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
  void selectsReadOnlyInvestigationForLatestFailure() {
    givenResponse(StopReason.TOOL_USE, call("investigate_failure", arguments("owner", "repo")));
    var result = router.route(new AssistantRequest("Investigate the latest failure in depth", "owner/repo"));
    assertEquals("investigate_failure", result.toolCalls().getFirst().name());
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

  @ParameterizedTest
  @ValueSource(strings = {
      "<thinking>Internal planning only</thinking>",
      "<thinking>Unclosed internal planning",
      "<thinking>Internal planning</thinking>There were no failures.",
      "There were no previous failures because the latest build passed."
  })
  void neverExposesFreeTextReasoningOrUnverifiedBuildClaims(String providerText) {
    givenResponse(StopReason.END_TURN, ContentBlock.fromText(providerText));
    var response = router.route(new AssistantRequest("failure?", "owner/repo", null,
        List.of(new AssistantTurn("assistant", "The latest build passed."))));
    assertTrue(response.message().startsWith("Would you like"));
    assertFalse(response.message().contains("<thinking>"));
    assertFalse(response.message().contains("no failures"));
    assertFalse(response.message().contains("no previous failures"));
    assertTrue(response.toolCalls().isEmpty());
  }

  @Test
  void requestsFreshFailuresAfterSuccessfulBuildContext() {
    givenResponse(StopReason.TOOL_USE, call("get_failed_runs", arguments("owner", "repo")));
    var response = router.route(new AssistantRequest("failure?", "owner/repo", null,
        List.of(new AssistantTurn("assistant", "The latest build passed."))));
    assertEquals("get_failed_runs", response.toolCalls().getFirst().name());
    String policy = capturedRequest.system().getFirst().text();
    assertTrue(policy.contains("does NOT mean there are no earlier failed runs"));
    assertTrue(policy.contains("Never answer live repository questions from conversation history alone"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"What is the weather?", "Tell me a joke", "Who won the football match?"})
  void returnsScopeMessageForUnrelatedQuestions(String question) {
    givenResponse(StopReason.TOOL_USE, call("clarify_request", Document.fromMap(Map.of(
        "reason", Document.fromString("out_of_scope")))));
    var response = router.route(new AssistantRequest(question, "owner/repo"));
    assertEquals("I can help with CI/CD pipelines, builds, deployment failures, and logs. "
        + "Please ask a pipeline-related question.", response.message());
    assertTrue(response.toolCalls().isEmpty());
  }

  @Test
  void acceptsOnlyControlledClarificationReasons() {
    givenResponse(StopReason.TOOL_USE, call("clarify_request", Document.fromMap(Map.of(
        "reason", Document.fromString("unsupported_action")))));
    assertTrue(router.route(new AssistantRequest("Deploy it", "owner/repo")).message().contains("cannot deploy"));
    givenResponse(StopReason.TOOL_USE, call("clarify_request", Document.fromMap(Map.of(
        "reason", Document.fromString("ambiguous"), "answer", Document.fromString("Made up build status")))));
    assertThrows(IllegalStateException.class, () -> router.route(new AssistantRequest("Check it", "owner/repo")));
    givenResponse(StopReason.TOOL_USE, call("clarify_request", Document.fromMap(Map.of(
        "reason", Document.fromString("made_up")))));
    assertThrows(IllegalStateException.class, () -> router.route(new AssistantRequest("Check it", "owner/repo")));
  }

  @Test
  void explainsGeneralConceptsWithoutRepositoryOrHistoryInExplanationRequest() {
    var requests = givenExplanation("general", "A pipeline is a sequence of automated build, test, and deployment stages.");
    var response = router.route(new AssistantRequest("What is a CI/CD pipeline?", "owner/repo", 42L,
        List.of(new AssistantTurn("assistant", "The latest build passed."))));
    assertEquals("A pipeline is a sequence of automated build, test, and deployment stages.", response.message());
    assertTrue(response.toolCalls().isEmpty());
    assertEquals(3, requests.size());
    var explanation = requests.get(2);
    assertNotNull(explanation.toolConfig().toolChoice().any());
    assertEquals(1, explanation.messages().size());
    assertEquals(1, explanation.messages().getFirst().content().size());
    assertEquals("What is a CI/CD pipeline?", explanation.messages().getFirst().content().getFirst().text());
    assertFalse(explanation.messages().toString().contains("owner/repo"));
    assertFalse(explanation.messages().toString().contains("latest build passed"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"<thinking>internal planning</thinking>Final answer", "<analysis>private", "&lt;thinking&gt;private", "```analysis private"})
  void rejectsInternalMarkupEvenInsideStructuredExplanation(String answer) {
    givenExplanation("general", answer);
    assertThrows(IllegalStateException.class,
        () -> router.route(new AssistantRequest("What is a pipeline?", "owner/repo")));
  }

  @Test
  void generalExplanationRechecksScopeAndRefusesLiveFacts() {
    givenExplanation("out_of_scope", "Ignored provider text");
    assertTrue(router.route(new AssistantRequest("Weather?", "owner/repo")).message().contains("pipeline-related"));
    givenExplanation("needs_live_data", "The build passed.");
    assertTrue(router.route(new AssistantRequest("Is the build green?", "owner/repo")).message().startsWith("Would you like"));
  }

  private List<ConverseRequest> givenExplanation(String kind, String answer) {
    var requests = new java.util.ArrayList<ConverseRequest>();
    doAnswer(invocation -> {
      Consumer<ConverseRequest.Builder> customize = invocation.getArgument(0);
      var builder = ConverseRequest.builder();
      customize.accept(builder);
      requests.add(builder.build());
      var block = isScopeRequest(builder.build()) ? scopeCall("pipeline_request") : requests.size() == 2
          ? call("explain_pipeline", Document.fromMap(Map.of()))
          : call("provide_pipeline_explanation", Document.fromMap(Map.of(
              "kind", Document.fromString(kind), "answer", Document.fromString(answer))));
      return ConverseResponse.builder().stopReason(StopReason.TOOL_USE)
          .output(output -> output.message(message -> message
              .role(ConversationRole.ASSISTANT).content(block))).build();
    }).when(client).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
    return requests;
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
          var request = builder.build();
          if (isScopeRequest(request)) {
            return ConverseResponse.builder().stopReason(StopReason.TOOL_USE)
                .output(output -> output.message(message -> message.content(scopeCall("pipeline_request")))).build();
          }
          capturedRequest = request;
          return ConverseResponse.builder().stopReason(reason)
              .output(output -> output.message(message -> message
                  .role(ConversationRole.ASSISTANT).content(blocks))).build();
        }).when(client).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"thank you", "THANK YOU!", "Thanks.", "Thank you so much!", "bye", "I love you", "love you!"})
  void socialClosuresNeverRepeatPreviousInvestigationOrCallAws(String message) {
    var response = router.route(new AssistantRequest(message, "owner/repo", 42L,
        List.of(new AssistantTurn("user", "Investigate run 42 in depth"),
            new AssistantTurn("assistant", "Observed test failure. Possible cause: simulation."))));
    assertTrue(response.toolCalls().isEmpty());
    assertTrue(response.message().contains("CI/CD"));
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @ValueSource(strings = {"out_of_scope", "social", "ambiguous"})
  void isolatedScopeGateStopsRoutingRegardlessOfFailureHistory(String kind) {
    doAnswer(invocation -> {
      Consumer<ConverseRequest.Builder> customize = invocation.getArgument(0);
      var builder = ConverseRequest.builder();
      customize.accept(builder);
      capturedRequest = builder.build();
      return ConverseResponse.builder().stopReason(StopReason.TOOL_USE)
          .output(output -> output.message(message -> message.content(scopeCall(kind)))).build();
    }).when(client).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
    var response = router.route(new AssistantRequest("Can you recommend a movie?", "owner/repo", 42L,
        List.of(new AssistantTurn("user", "Investigate that failure"))));
    assertTrue(response.toolCalls().isEmpty());
    assertTrue(isScopeRequest(capturedRequest));
    assertEquals(1, capturedRequest.messages().getFirst().content().size());
    assertEquals("Can you recommend a movie?", capturedRequest.messages().getFirst().content().getFirst().text());
    assertFalse(capturedRequest.messages().toString().contains("Investigate that failure"));
    assertFalse(capturedRequest.messages().toString().contains("owner/repo"));
    verify(client, times(1)).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
  }

  @Test
  void gratitudeWithAnExplicitNewRequestStillRoutesNormally() {
    givenResponse(StopReason.TOOL_USE, call("get_failure_logs", arguments("owner", "repo")));
    var response = router.route(new AssistantRequest("Thanks, now show the failure logs", "owner/repo"));
    assertEquals("get_failure_logs", response.toolCalls().getFirst().name());
  }

  @Test
  void invalidScopeResponseFailsClosedWithoutRouting() {
    when(client.converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any()))
        .thenReturn(ConverseResponse.builder().stopReason(StopReason.END_TURN)
            .output(output -> output.message(message -> message.content(ContentBlock.fromText("Just run the old tool")))).build());
    assertThrows(IllegalStateException.class,
        () -> router.route(new AssistantRequest("A new question", "owner/repo")));
    verify(client, times(1)).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
  }

  private boolean isScopeRequest(ConverseRequest request) {
    return request.toolConfig().tools().getFirst().toolSpec().name().equals("classify_current_message");
  }

  @Test
  void generalScopeGoesDirectlyToIsolatedExplanationWithoutRepositoryRouting() {
    var requests = new java.util.ArrayList<ConverseRequest>();
    doAnswer(invocation -> {
      Consumer<ConverseRequest.Builder> customize = invocation.getArgument(0);
      var builder = ConverseRequest.builder();
      customize.accept(builder);
      var request = builder.build();
      requests.add(request);
      var block = isScopeRequest(request) ? scopeCall("general")
          : call("provide_pipeline_explanation", Document.fromMap(Map.of(
              "kind", Document.fromString("general"), "answer", Document.fromString("A pipeline automates build and test steps."))));
      return ConverseResponse.builder().stopReason(StopReason.TOOL_USE)
          .output(output -> output.message(message -> message.content(block))).build();
    }).when(client).converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any());
    var response = router.route(new AssistantRequest("What is a pipeline?", "owner/repo", 42L,
        List.of(new AssistantTurn("user", "Investigate the last failure"))));
    assertEquals("A pipeline automates build and test steps.", response.message());
    assertTrue(response.toolCalls().isEmpty());
    assertEquals(2, requests.size());
    for (var request : requests) {
      assertEquals(1, request.messages().getFirst().content().size());
      assertFalse(request.messages().toString().contains("owner/repo"));
      assertFalse(request.messages().toString().contains("Investigate the last failure"));
    }
  }

  private ContentBlock scopeCall(String kind) {
    return call("classify_current_message", Document.fromMap(Map.of("kind", Document.fromString(kind))));
  }
}
