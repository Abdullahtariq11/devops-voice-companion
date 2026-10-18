package com.abdullah.devopsvoicecompanion.bedrock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the actual summarizer with a mocked SDK client, without AWS configuration. */
class BedrockSummarizerUnitTests {
  private BedrockRuntimeClient client;
  private BedrockSummarizer summarizer;
  private ConverseRequest capturedRequest;

  @BeforeEach
  void setUp() {
    client = mock(BedrockRuntimeClient.class);
    summarizer = new BedrockSummarizer(client);
  }

  @Test
  void sendsRepositoryAndFailureEvidenceWithBoundedInferenceSettings() {
    givenResponse(ContentBlock.fromText("The test failed."));

    assertEquals("The test failed.", summarizer.summarize("owner/repo", "Expected 100, actual 90."));

    assertEquals("amazon.nova-lite-v1:0", capturedRequest.modelId());
    assertEquals(ConversationRole.USER, capturedRequest.messages().getFirst().role());
    assertEquals("Repository: owner/repo\n\nExpected 100, actual 90.",
        capturedRequest.messages().getFirst().content().getFirst().text());
    assertEquals(200, capturedRequest.inferenceConfig().maxTokens());
    assertEquals(0.2F, capturedRequest.inferenceConfig().temperature());
    String instructions = capturedRequest.system().getFirst().text();
    assertTrue(instructions.contains("Use only facts present in the details"));
    assertTrue(instructions.contains("not a missing log"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\n\t"})
  void rejectsMissingEvidenceBeforeCallingAws(String evidence) {
    assertThrows(IllegalArgumentException.class, () -> summarizer.summarize("owner/repo", evidence));
    verifyNoInteractions(client);
  }

  @Test
  void rejectsEvidenceAboveTheInputLimit() {
    assertThrows(IllegalArgumentException.class,
        () -> summarizer.summarize("owner/repo", "x".repeat(10_001)));
    verifyNoInteractions(client);
  }

  @Test
  void acceptsEvidenceAtTheInputLimit() {
    givenResponse(ContentBlock.fromText("Summary"));

    assertEquals("Summary", summarizer.summarize("owner/repo", "x".repeat(10_000)));
  }

  @Test
  void joinsTextBlocksAndIgnoresNonTextBlocks() {
    givenResponse(ContentBlock.fromText(" First sentence."),
        ContentBlock.builder().toolUse(ToolUseBlock.builder()
            .toolUseId("unused").name("unused").build()).build(),
        ContentBlock.fromText("Second sentence. "));

    assertEquals("First sentence. Second sentence.", summarizer.summarize("owner/repo", "Failure"));
  }

  @Test
  void returnsEmptyTextForAnEmptyModelMessage() {
    givenResponse();

    assertEquals("", summarizer.summarize("owner/repo", "Failure"));
  }

  @Test
  void propagatesSdkErrorsForTheToolToHandle() {
    var failure = SdkClientException.create("Service unavailable");
    when(client.converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any()))
        .thenThrow(failure);

    assertSame(failure, assertThrows(SdkClientException.class,
        () -> summarizer.summarize("owner/repo", "Failure")));
  }

  private void givenResponse(ContentBlock... blocks) {
    when(client.converse(org.mockito.ArgumentMatchers.<Consumer<ConverseRequest.Builder>>any()))
        .thenAnswer(invocation -> {
          Consumer<ConverseRequest.Builder> customize = invocation.getArgument(0);
          var builder = ConverseRequest.builder();
          customize.accept(builder);
          capturedRequest = builder.build();
          return ConverseResponse.builder().output(output -> output.message(message ->
              message.role(ConversationRole.ASSISTANT).content(blocks))).build();
        });
  }
}
